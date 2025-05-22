const formatDate = (dateString) => {
try {
    const date = new Date(dateString);

    if (isNaN(date.getTime())) {
    return new Date().toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    });
    }

    return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
    });
} catch (error) {
    console.error('날짜 형식 변환 오류:', error);
    return new Date().toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
    });
}
};