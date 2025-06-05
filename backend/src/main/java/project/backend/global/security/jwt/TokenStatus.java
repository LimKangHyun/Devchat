package project.backend.global.security.jwt;

public enum TokenStatus {
	VALID,
	EXPIRED,
	INVALID_SIGNATURE,
	MALFORMED,
	UNKNOWN_ERROR
}
