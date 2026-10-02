package com.omegas.prohub.autocal

import org.junit.Assert.*
import org.junit.Test

class TypicalInjectionBandsTest {
    @Test fun `empty bands remain unknown and upper bound belongs to next band`() {
        val rows=listOf(EquivalenceLedger.Obs(0,1800.0,0.6,4.0),EquivalenceLedger.Obs(1,2400.0,0.8,4.1),EquivalenceLedger.Obs(2,3000.0,0.9,4.5))
        val out=TypicalInjectionBands.json(rows)
        assertEquals(5,out.length());val first=out.getJSONObject(0)
        assertEquals(2,first.getInt("samples"));assertEquals(2100.0,first.getDouble("rpmMedian"),0.0);assertEquals(0.7,first.getDouble("mapMedian"),1e-9)
        assertEquals(1,out.getJSONObject(1).getInt("samples"));assertTrue(out.getJSONObject(2).isNull("rpmMedian"));assertTrue(out.getJSONObject(2).isNull("mapMedian"))
    }
    @Test fun `consultation leaves input intact and ledger uses petrol only`() {
        val ledger=EquivalenceLedger();repeat(7){ledger.accept(EquivalenceLedger.Frame(it*280L,"GASOLINA",2200.0,0.7,6.0))}
        repeat(7){ledger.accept(EquivalenceLedger.Frame(5000+it*280L,"GNV",3200.0,0.8,6.0))}
        val before=ledger.index().toString();val typical=ledger.typicalBandsJson().getJSONObject(2)
        assertEquals(2200.0,typical.getDouble("rpmMedian"),0.0);assertEquals(0.7,typical.getDouble("mapMedian"),1e-9)
        assertEquals(before,ledger.index().toString())
    }
}
