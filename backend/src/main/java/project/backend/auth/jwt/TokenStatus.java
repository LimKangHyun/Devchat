package project.backend.auth.jwt;

public enum TokenStatus {
	VALID,
	EXPIRED,
	INVALID_SIGNATURE,
	MALFORMED,
	UNKNOWN_ERROR
}
