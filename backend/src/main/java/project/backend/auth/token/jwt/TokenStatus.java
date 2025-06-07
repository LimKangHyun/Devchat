package project.backend.auth.token.jwt;

public enum TokenStatus {
	VALID,
	EXPIRED,
	INVALID_SIGNATURE,
	MALFORMED,
	UNKNOWN_ERROR
}
