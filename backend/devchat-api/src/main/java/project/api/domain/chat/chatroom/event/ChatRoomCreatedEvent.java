package project.api.domain.chat.chatroom.event;

public record ChatRoomCreatedEvent(Long roomId, String repositoryUrl, Long ownerId) {}