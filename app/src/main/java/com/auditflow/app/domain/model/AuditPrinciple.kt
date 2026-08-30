package com.auditflow.app.domain.model

/**
 * Core epistemic verification principle of AuditFlow:
 *
 * EXISTS
 * ≠
 * CONNECTED
 * ≠
 * EXECUTED
 * ≠
 * VALIDATED
 * ≠
 * VERIFIED
 * ≠
 * PRODUCES_EXPECTED_RESULT
 */
enum class AuditPrincipleLevel(val label: String, val description: String) {
    EXISTS("EXISTS", "Code file or artifact is present on disk or remote host"),
    CONNECTED("CONNECTED", "Transport link to repository, VCS, or build runner is open"),
    EXECUTED("EXECUTED", "Process, compiler, or analyzer has completed an execution run"),
    VALIDATED("VALIDATED", "Syntactic schema or structure meets formal prerequisite rules"),
    VERIFIED("VERIFIED", "Cryptographic or mathematical proof of semantic correctness established"),
    PRODUCES_EXPECTED_RESULT("PRODUCES_EXPECTED_RESULT", "Output strictly satisfies domain specification under all test invariants")
}
