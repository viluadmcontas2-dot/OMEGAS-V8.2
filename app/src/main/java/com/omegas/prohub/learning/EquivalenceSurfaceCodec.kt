package com.omegas.prohub.learning

import org.json.JSONArray
import org.json.JSONObject

/** Persistence-boundary codec. Runtime arithmetic never depends on JSON. */
internal object EquivalenceSurfaceCodec {
    const val REPRESENTATION = "SPARSE_MOMENTS_ONLY"

    fun encode(snapshot: EquivalenceSurface.Snapshot): String {
        val nodes = JSONArray()
        snapshot.nodes.forEach { node ->
            nodes.put(
                JSONObject()
                    .put("lane", node.lane.name)
                    .put("index", node.index)
                    .put("sumW", node.sumW)
                    .put("sumW2", node.sumW2)
                    .put("sumWTinj", node.sumWTinj)
                    .put("sumWTinj2", node.sumWTinj2)
                    .put("materialRevision", node.materialRevision),
            )
        }
        val root = JSONObject()
            .put("schema", snapshot.schema)
            .put("representation", REPRESENTATION)
            .put("minRpm", snapshot.minRpm)
            .put("maxRpm", snapshot.maxRpm)
            .put("rpmStep", snapshot.rpmStep)
            .put("minMapBar", snapshot.minMapBar)
            .put("maxMapBar", snapshot.maxMapBar)
            .put("mapStepBar", snapshot.mapStepBar)
            .put("legacySeededRegions", snapshot.legacySeededRegions)
            .put("nodes", nodes)
        snapshot.legacySeedProvenance?.let { root.put("legacySeedProvenance", it) }
        return root.toString()
    }

    fun decode(encoded: String): EquivalenceSurface.Snapshot {
        val root = JSONObject(encoded)
        require(root.optString("representation") == REPRESENTATION) { "Unsupported equivalence representation" }
        val nodesJson = root.optJSONArray("nodes") ?: JSONArray()
        val nodes = ArrayList<EquivalenceSurface.SnapshotNode>(nodesJson.length())
        repeat(nodesJson.length()) { index ->
            val raw = nodesJson.getJSONObject(index)
            val lane = FuelLane.valueOf(raw.getString("lane"))
            nodes += EquivalenceSurface.SnapshotNode(
                lane = lane,
                index = raw.getInt("index"),
                sumW = raw.getDouble("sumW"),
                sumW2 = raw.getDouble("sumW2"),
                sumWTinj = raw.getDouble("sumWTinj"),
                sumWTinj2 = raw.getDouble("sumWTinj2"),
                materialRevision = raw.optLong("materialRevision", 0L),
            )
        }
        return EquivalenceSurface.Snapshot(
            schema = root.getString("schema"),
            minRpm = root.getDouble("minRpm"),
            maxRpm = root.getDouble("maxRpm"),
            rpmStep = root.getDouble("rpmStep"),
            minMapBar = root.getDouble("minMapBar"),
            maxMapBar = root.getDouble("maxMapBar"),
            mapStepBar = root.getDouble("mapStepBar"),
            nodes = nodes,
            legacySeededRegions = root.optInt("legacySeededRegions", 0).coerceAtLeast(0),
            legacySeedProvenance = root.optString("legacySeedProvenance").takeIf { it.isNotBlank() },
        )
    }
}
