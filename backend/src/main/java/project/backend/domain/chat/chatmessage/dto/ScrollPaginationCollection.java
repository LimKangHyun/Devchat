package project.backend.domain.chat.chatmessage.dto;

import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

//커서기반 무한스크롤
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ScrollPaginationCollection<T> {

	private final List<T> itemsWithNextCursor;
	private final int size;

	public static <T> ScrollPaginationCollection<T> of(List<T> itemsWithNextCursor, int size) {
		return new ScrollPaginationCollection<>(itemsWithNextCursor, size);
	}

	public List<T> getCurrentScrollItems() {
		if (isLastScroll()) {
			return itemsWithNextCursor;
		}
		return itemsWithNextCursor.subList(0, size);
	}

	public T getNextCursor() {
		if (isLastScroll()) {
			return null;
		}
		return itemsWithNextCursor.get(size - 1);
	}

	public boolean isLastScroll() {
		return itemsWithNextCursor.size() < size;
	}
}
