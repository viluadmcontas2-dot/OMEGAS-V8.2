package com.omegas.prohub.carinfo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.Parcel
import com.omegas.prohub.util.RingLog
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Fonte OPCIONAL e PASSIVA do painel do carro vinda da multimídia (Car Info OEM, `com.can.activity`).
 *
 * SOMENTE LEITURA. Usa apenas, do contrato ICanBus (docs: "Car Info da multimídia"):
 *  - ICanUI tx 1 `getCanService("CanBusManager")` (devolve o binder do ICanBus);
 *  - ICanBus tx 1 `registerListener` / tx 2 `unregisterListener` (passivos);
 *  - ICanBus tx 18 `getDashBoardInfo`, UMA vez por conexão, para a foto inicial (o registro não
 *    manda foto). INCERTO: tx 18 não tem efeito colateral confirmado no código inspecionado, mas
 *    nunca foi chamado no carro.
 * NUNCA usar `setConfig`, `set*Property`, `setCarProperty`, `setDiagnose` nem `getCarPropValues`
 * (tx 30 pode transmitir pacotes na CAN). Este app não escreve na CAN nem na ECU por aqui.
 *
 * O bind em runtime é INCERTO (SELinux/assinatura): falha = estado "indisponivel", sem crash,
 * com nova tentativa espaçada pelo [CarInfoSupervisor].
 */
class CarInfoSource(
    context: Context,
    private val log: RingLog,
    private val onSample: (CarInfoSample) -> Unit,
) {
    companion object {
        const val PACKAGE = "com.can.activity"
        const val SERVICE_CLASS = "com.can.ui.CanPopWind"
        const val CAN_SERVICE_NAME = "CanBusManager"

        const val ICAN_UI = "com.autoai.canbus.ICanUI"
        const val ICAN_BUS = "com.autoai.canbus.ICanBus"
        const val ICAN_BUS_LISTENER = "com.autoai.canbus.ICanBusListener"

        const val TX_GET_CAN_SERVICE = 1
        const val TX_REGISTER_LISTENER = 1
        const val TX_UNREGISTER_LISTENER = 2
        const val TX_GET_DASHBOARD_INFO = 18
        const val TX_ON_DASHBOARD_INFO_CHANGED = 6
        const val LISTENER_TX_COUNT = 17
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "omegas-carinfo").apply { isDaemon = true } }

    @Volatile private var bus: IBinder? = null
    @Volatile private var bound = false
    private val deathRecipient = IBinder.DeathRecipient { supervisor.onFailure("A multimídia encerrou o serviço do painel") }

    val supervisor = CarInfoSupervisor(
        clock = System::currentTimeMillis,
        bind = ::requestBind,
        unbind = ::releaseBinding,
        schedule = { delay, action -> main.postDelayed({ action() }, delay) },
    )

    private val listener = ListenerBinder()

    private inner class ListenerBinder : Binder(), IInterface {
        init { attachInterface(this, ICAN_BUS_LISTENER) }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code !in 1..LISTENER_TX_COUNT) return super.onTransact(code, data, reply, flags)
            // O cliente OEM implementa as 17; aqui todas respondem, só a 6 (painel) é lida.
            data.enforceInterface(ICAN_BUS_LISTENER)
            if (code == TX_ON_DASHBOARD_INFO_CHANGED) {
                try {
                    DashBoardInfoDecoder.decodeNullable(ParcelFieldReader(data))?.let(::deliver)
                } catch (e: Exception) {
                    log.add("WARN", "CARINFO", "Painel ilegível: ${e.message ?: e.javaClass.simpleName}")
                }
            }
            reply?.writeNoException()
            return true
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service == null) { supervisor.onFailure("Multimídia sem serviço do painel"); return }
            worker.execute { attach(service) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bus = null
            supervisor.onFailure("Multimídia desconectou")
        }

        override fun onBindingDied(name: ComponentName?) = supervisor.onFailure("Ligação com a multimídia morreu")
        override fun onNullBinding(name: ComponentName?) = supervisor.onFailure("Multimídia recusou a ligação")
    }

    fun start() = supervisor.start()

    fun stop() = supervisor.stop()

    fun close() {
        stop()
        worker.shutdown()
    }

    fun json(): JSONObject = supervisor.json()

    private fun deliver(sample: CarInfoSample) {
        supervisor.onSample(sample)
        try { onSample(sample) } catch (e: Exception) {
            log.add("WARN", "CARINFO", "Falha ao registrar painel: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun requestBind(): Boolean {
        val intent = Intent().setComponent(ComponentName(PACKAGE, SERVICE_CLASS))
        return try {
            val ok = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            bound = ok
            if (!ok) {
                // bindService=false ainda exige unbind para não vazar a conexão.
                try { appContext.unbindService(connection) } catch (_: Exception) { /* nada ligado */ }
            }
            ok
        } catch (e: SecurityException) {
            bound = false
            log.add("INFO", "CARINFO", "Multimídia negou acesso ao painel: ${e.message ?: "sem permissão"}")
            false
        }
    }

    private fun attach(gatewayBinder: IBinder) {
        try {
            val canBus = transactForBinder(gatewayBinder, ICAN_UI, TX_GET_CAN_SERVICE) { it.writeString(CAN_SERVICE_NAME) }
                ?: throw IllegalStateException("Multimídia sem CanBusManager")
            canBus.linkToDeath(deathRecipient, 0)
            transactVoid(canBus, ICAN_BUS, TX_REGISTER_LISTENER) { it.writeStrongBinder(listener) }
            bus = canBus
            supervisor.onConnected()
            log.add("INFO", "CARINFO", "Painel da multimídia ligado (só leitura)")
            readInitialSnapshot(canBus)
        } catch (e: Exception) {
            supervisor.onFailure(e.message ?: e.javaClass.simpleName)
        }
    }

    /** tx 18 uma vez: foto inicial. Falha aqui não derruba a conexão (os eventos continuam). */
    private fun readInitialSnapshot(canBus: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ICAN_BUS)
            if (!canBus.transact(TX_GET_DASHBOARD_INFO, data, reply, 0)) return
            reply.readException()
            DashBoardInfoDecoder.decodeNullable(ParcelFieldReader(reply))?.let(::deliver)
        } catch (e: Exception) {
            log.add("INFO", "CARINFO", "Foto inicial do painel indisponível: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun releaseBinding() {
        val canBus = bus
        bus = null
        if (canBus != null) {
            worker.execute {
                try { canBus.unlinkToDeath(deathRecipient, 0) } catch (_: Exception) { /* já morto */ }
                try { transactVoid(canBus, ICAN_BUS, TX_UNREGISTER_LISTENER) { it.writeStrongBinder(listener) } } catch (_: Exception) { /* serviço já foi */ }
            }
        }
        if (bound) {
            bound = false
            main.post { try { appContext.unbindService(connection) } catch (_: Exception) { /* já solto */ } }
        }
    }

    private inline fun transactVoid(target: IBinder, descriptor: String, code: Int, write: (Parcel) -> Unit) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            write(data)
            if (!target.transact(code, data, reply, 0)) throw IllegalStateException("Transação $code não reconhecida")
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private inline fun transactForBinder(target: IBinder, descriptor: String, code: Int, write: (Parcel) -> Unit): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            write(data)
            if (!target.transact(code, data, reply, 0)) return null
            reply.readException()
            return reply.readStrongBinder()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}

/** Adaptador do Parcel real para o decodificador puro. */
class ParcelFieldReader(private val parcel: Parcel) : DashBoardFieldReader {
    override fun remainingBytes(): Int = parcel.dataAvail()
    override fun readInt(): Int = parcel.readInt()
    override fun readFloat(): Float = parcel.readFloat()
}
