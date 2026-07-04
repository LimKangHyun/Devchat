package project.api.auth.jwt;

public enum TokenStatus {
	VALID,
	EXPIRED,
	INVALID_SIGNATURE,
	MALFORMED,
	UNKNOWN_ERROR
}
