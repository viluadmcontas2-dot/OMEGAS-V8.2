package com.omegas.prohub.telemetry

import com.omegas.prohub.ecu.AutoCalProtocol
import com.omegas.prohub.usb.UsbProtocolReply
import org.json.JSONArray
import org.json.JSONObject

/** Captura opcional, somente leitura. Bytes opacos nunca viram referências calibradas. */
class LevelSensorSnapshot(private val clock:()->Long=System::currentTimeMillis) {
    companion object {
        val FIELDS=listOf(
            AutoCalProtocol.Field("TIPO_SENSORE",36,AutoCalProtocol.Encoding.U8,AutoCalProtocol.Shape.SCALAR),
            AutoCalProtocol.Field("RIF_SENSORE",37,AutoCalProtocol.Encoding.U8,AutoCalProtocol.Shape.VECTOR),
            AutoCalProtocol.Field("LEVEL_FILTERS",276,AutoCalProtocol.Encoding.U16_LE,AutoCalProtocol.Shape.VECTOR,2),
            AutoCalProtocol.Field("LEVEL_LEDS",300,AutoCalProtocol.Encoding.U8,AutoCalProtocol.Shape.VECTOR,5),
            AutoCalProtocol.Field("TANK_VOL",313,AutoCalProtocol.Encoding.U16_LE,AutoCalProtocol.Shape.INDEXED,1,index=0),
        )
        const val MAX_READING_AGE_MS=2_000L
    }
    private val lock=Any()
    private var snapshot=JSONObject().put("state","UNKNOWN").put("available",false)
    private val estimator=LevelEstimator()
    private var reading:LevelEstimator.Reading?=null
    private var readingAt=0L
    private var session=0L
    fun resetForSession(id:Long)=synchronized(lock){if(id!=session){session=id;snapshot=JSONObject().put("state","UNKNOWN").put("available",false);reading=null}}
    fun read(sessionId:Long,currentSession:()->Long,transaction:(ByteArray)->UsbProtocolReply):JSONObject {
        resetForSession(sessionId)
        synchronized(lock){snapshot=JSONObject().put("state","READING").put("available",false).put("sessionId",sessionId)}
        val fields=JSONArray();var received=0;var error:String?=null
        try {
            require(sessionId>0 && currentSession()==sessionId){"Sessão USB não confirmada"}
            for(field in FIELDS){
                require(currentSession()==sessionId){"Sessão USB mudou"}
                val row=JSONObject().put("key",field.key).put("sc",field.address).put("index",field.index ?: JSONObject.NULL).put("observedAt",clock())
                try {
                    val reply=transaction(AutoCalProtocol.read(field))
                    require(currentSession()==sessionId){"Sessão USB mudou"}
                    require(reply.ok && reply.status==0x53 && reply.payload.isNotEmpty()){reply.error.ifBlank {"Sem resposta confirmada"}}
                    received++
                    row.put("rawHex",reply.payload.joinToString(""){"%02X".format(it.toInt() and 255)})
                        .put("status","UNDECODED").put("label","não decodificado").put("ack",reply.status)
                    // Metadados demonstrados no FORMULAS.md; shape deve coincidir.
                    // Tipo e referências continuam opacos: largura/enum não comprovados.
                    if(field.address in setOf(276,300,313))try{
                        val decoded=AutoCalProtocol.decode(field,reply.status,reply.payload)
                        require(decoded.elementCount==field.expectedElementsHint){"Tamanho divergente"}
                        row.put("rawValues",JSONArray(decoded.rawValues.toList()))
                        if(field.address==276)row.put("coefficients",JSONArray(decoded.rawValues.map { it/32768.0 }))
                        if(field.address==313)row.put("nominalParameter",decoded.rawValues[0]*1000.0/32768)
                        row.put("status","METADATA").put("label","metadados lidos; lógica do sensor não comprovada")
                    }catch(e:Exception){row.put("decodeError",e.message)}
                }catch(e:Exception){row.put("status","NOT_READ").put("label","não lido").put("error",e.message)}
                fields.put(row)
            }
            require(currentSession()==sessionId){"Sessão USB mudou"}
        }catch(e:Exception){error=e.message}
        synchronized(lock){snapshot=JSONObject().put("ok",error==null).put("state",if(error!=null)"UNKNOWN" else if(received==5)"READY" else "PARTIAL")
            .put("available",received>0 && error==null).put("sessionId",sessionId).put("fields",fields)
            .put("error",error ?: JSONObject.NULL).put("modelVerified",false)}
        return json()
    }
    fun accept(raw:Int,at:Long)=synchronized(lock){reading=estimator.accept(raw);readingAt=at}
    fun json():JSONObject=synchronized(lock){
        val out=JSONObject(snapshot.toString())
        val current=reading?.takeIf { clock()-readingAt in 0..MAX_READING_AGE_MS }
        out.put("percent",current?.percent ?: JSONObject.NULL).put("position",current?.position ?: JSONObject.NULL)
            .put("filteredRaw",current?.filteredRaw ?: JSONObject.NULL).put("leds",current?.leds ?: JSONObject.NULL)
            .put("proxy",current?.proxy ?: true).put("readingAt",if(current!=null)readingAt else JSONObject.NULL)
            .put("label",if(current==null)"Sem leitura atual" else "Proxy de nível · perfil do sensor não comprovado")
            .put("modelReason","Tipo, cheio, troca FAST/SLOW e comparação dos LEDs ainda exigem prova")
            .put("volumeMeasured",false).put("PHYSICAL_VALIDATION_CLAIMED",false)
    }
}
