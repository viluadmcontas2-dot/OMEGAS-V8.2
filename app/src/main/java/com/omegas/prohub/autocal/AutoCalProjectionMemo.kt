package com.omegas.prohub.autocal

import org.json.JSONObject

/** Memoiza apenas a projeção do último snapshot material; não possui I/O nem clock. */
internal class AutoCalProjectionMemo {
    private var snapshotHash = ""
    private var projection: JSONObject? = null
    var recomputeCount: Long = 0L
        private set

    fun resolve(hash: String, compute: () -> JSONObject): JSONObject {
        if (hash.isBlank() || hash != snapshotHash || projection == null) {
            projection = JSONObject(compute().toString())
            snapshotHash = hash
            recomputeCount += 1L
        }
        return JSONObject(requireNotNull(projection).toString())
    }

    fun clear() {
        snapshotHash = ""
        projection = null
        recomputeCount = 0L
    }
}
