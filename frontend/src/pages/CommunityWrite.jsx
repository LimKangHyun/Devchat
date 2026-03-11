import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosInstance from '../components/api/axiosInstance';
import ReactMarkdown from 'react-markdown';
import styles from './CommunityWrite.module.css';
import remarkBreaks from "remark-breaks";
import remarkGfm from 'remark-gfm';

const TAGS = ['백엔드', '프론트엔드', '풀스택', '알고리즘', '데브옵스', 'AI/ML', '모바일'];
const TECH_STACKS = ['Java', 'Spring', 'Python', 'React', 'Vue', 'Node.js', 'Kotlin', 'TypeScript', 'Docker', 'Kubernetes'];
const MODES = ['온라인', '오프라인', '혼합'];

const CommunityWrite = () => {
  const navigate = useNavigate();

  const [form, setForm] = useState({
    title: '',
    content: '',
    maxCount: '',
    deadline: '',
    tag: '',
    techStacks: [],
    mode: '온라인',
    chatRoomId: '',
  });

  const [chatRooms, setChatRooms] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isPreview, setIsPreview] = useState(false);

  useEffect(() => {
    const fetchChatRooms = async () => {
      try {
        const res = await axiosInstance.get('/chat-rooms');
        setChatRooms(res.data.content ?? res.data);
      } catch (err) {
        console.error('채팅방 목록 불러오기 실패', err);
      }
    };
    fetchChatRooms();
  }, []);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
  };

  const toggleTechStack = (stack) => {
    setForm(prev => ({
      ...prev,
      techStacks: prev.techStacks.includes(stack)
        ? prev.techStacks.filter(s => s !== stack)
        : [...prev.techStacks, stack]
    }));
  };

  const handleSubmit = async () => {
    if (!form.title.trim()) return alert('제목을 입력해주세요.');
    if (!form.content.trim()) return alert('내용을 입력해주세요.');
    if (!form.maxCount) return alert('모집 인원을 입력해주세요.');
    if (!form.chatRoomId) return alert('스터디방을 선택해주세요.');

    try {
      setIsSubmitting(true);

      const res = await axiosInstance.post('/community', {
        title: form.title,
        content: form.content,
        maxCount: Number(form.maxCount),
        deadline: form.deadline || null,
        tag: form.tag || null,
        techStacks: form.techStacks,
        mode: form.mode,
        chatRoomId: Number(form.chatRoomId),
      });

      navigate(`/community/${res.data.id}`);
    } catch (err) {
      alert(err.response?.data?.message || '글 작성에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.card}>

        {/* 헤더 */}
        <div className={styles.cardHeader}>
          <h1 className={styles.title}>스터디 모집글 작성</h1>
          <p className={styles.subtitle}>함께할 스터디원을 모집해보세요</p>
        </div>

        <div className={styles.form}>

          {/* 스터디방 선택 */}
          <div className={styles.field}>
            <label className={styles.label}>
              스터디방 <span className={styles.required}>*</span>
            </label>
            <select
              name="chatRoomId"
              value={form.chatRoomId}
              onChange={handleChange}
              className={styles.select}
            >
              <option value="">스터디방을 선택해주세요</option>
              {chatRooms.map(room => (
                <option key={room.roomId} value={room.roomId}>{room.roomName}</option>
              ))}
            </select>
          </div>

          {/* 제목 */}
          <div className={styles.field}>
            <label className={styles.label}>
              제목 <span className={styles.required}>*</span>
            </label>
            <input
              type="text"
              name="title"
              value={form.title}
              onChange={handleChange}
              placeholder="스터디 제목을 입력해주세요"
              className={styles.input}
              maxLength={100}
            />
            <span className={styles.charCount}>{form.title.length}/100</span>
          </div>

          {/* 태그 / 진행방식 / 인원 / 마감일 */}
          <div className={styles.row}>
            <div className={styles.field}>
              <label className={styles.label}>카테고리</label>
              <select name="tag" value={form.tag} onChange={handleChange} className={styles.select}>
                <option value="">선택 안 함</option>
                {TAGS.map(tag => (
                  <option key={tag} value={tag}>{tag}</option>
                ))}
              </select>
            </div>

            <div className={styles.field}>
              <label className={styles.label}>진행 방식</label>
              <select name="mode" value={form.mode} onChange={handleChange} className={styles.select}>
                {MODES.map(mode => (
                  <option key={mode} value={mode}>{mode}</option>
                ))}
              </select>
            </div>

            <div className={styles.field}>
              <label className={styles.label}>
                모집 인원 <span className={styles.required}>*</span>
              </label>
              <input
                type="number"
                name="maxCount"
                value={form.maxCount}
                onChange={handleChange}
                placeholder="최대 인원"
                className={styles.input}
                min={2}
                max={20}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label}>마감일</label>
              <input
                type="date"
                name="deadline"
                value={form.deadline}
                onChange={handleChange}
                className={styles.input}
                min={new Date().toISOString().split('T')[0]}
              />
            </div>
          </div>

          {/* 기술 스택 */}
          <div className={styles.field}>
            <label className={styles.label}>기술 스택</label>
            <div className={styles.stackGrid}>
              {TECH_STACKS.map(stack => (
                <button
                  key={stack}
                  type="button"
                  onClick={() => toggleTechStack(stack)}
                  className={`${styles.stackChip} ${form.techStacks.includes(stack) ? styles.stackChipActive : ''}`}
                >
                  {stack}
                </button>
              ))}
            </div>
          </div>

          {/* 내용 */}
          <div className={styles.field}>
            <label className={styles.label}>
              내용 <span className={styles.required}>*</span>
            </label>

            <div className={styles.previewToggle}>
              <button
                type="button"
                onClick={() => setIsPreview(false)}
                className={!isPreview ? styles.toggleActive : styles.toggle}
              >
                작성
              </button>
              <button
                type="button"
                onClick={() => setIsPreview(true)}
                className={isPreview ? styles.toggleActive : styles.toggle}
              >
                미리보기
              </button>
            </div>

            {!isPreview ? (
              <textarea
                name="content"
                value={form.content}
                onChange={handleChange}
                placeholder={`Markdown 문법을 사용할 수 있습니다.

예시)
# 스터디 소개
- 주 1회 온라인 미팅
- 매주 알고리즘 문제 풀이
- 백준 실버 이상 권장

🔗 링크 추가: [링크이름](https://주소)`}
                className={styles.textarea}
                rows={12}
              />
            ) : (
              <div className={styles.previewBox}>
                <ReactMarkdown
                  remarkPlugins={[remarkGfm, remarkBreaks]}
                  components={{
                    a: ({ href, children }) => (
                      <a href={href} target="_blank" rel="noopener noreferrer">
                        {children}
                      </a>
                    )
                  }}
                >
                  {form.content || "작성된 내용이 여기에 미리보기로 표시됩니다."}
                </ReactMarkdown>
              </div>
            )}
          </div>

          {/* 버튼 */}
          <div className={styles.buttonRow}>
            <button
              type="button"
              onClick={() => navigate('/community')}
              className={styles.cancelButton}
            >
              취소
            </button>
            <button
              type="button"
              onClick={handleSubmit}
              disabled={isSubmitting}
              className={styles.submitButton}
            >
              {isSubmitting ? '등록 중...' : '모집글 등록'}
            </button>
          </div>

        </div>
      </div>
    </div>
  );
};

export default CommunityWrite;