package com.omegas.v7.runtime

import com.omegas.prohub.blue.BlueCausalEngine

/**
 * Transitional source-compatibility alias only. There is no V7 equivalence
 * implementation behind this name: every call is the single Blue engine.
 * Remove the alias after all callers have migrated to the Blue package.
 */
typealias V7EquivalenceEngine = BlueCausalEngine
