package project.backend.domain.member.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum ProviderType {
	LOCAL,
	GITHUB
}
