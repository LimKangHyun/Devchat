import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axiosInstance from '../components/api/axiosInstance';
import styles from './CommunityWrite.module.css';

const TAGS = ['백엔드', '프론트엔드', '풀스택', '알고리즘', '데브옵스', 'AI/ML', '모바일'];
const TECH_STACKS = ['Java', 'Spring', 'Python', 'React', 'Vue', 'Node.js', 'Kotlin', 'TypeScript', 'Docker', 'Kubernetes'];
const MODES = ['온라인', '오프라인', '혼합'];

const CommunityEdit = () => {
  const navigate = useNavigate();
  const { postId } = useParams();

  const [form, setForm] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const fetchPost = async () => {
      try {
        const res = await axiosInstance.get(`/community/${postId}`);
        const post = res.data;
        setForm({
          title: post.title,
          content: post.content,
          maxCount: post.maxCount,
          deadline: post.deadline || '',
          tag: post.tag || '',
          techStacks: post.techStacks || [],
          mode: post.mode || '온라인',
        });
      } catch (err) {
        alert('게시글을 불러올 수 없습니다.');
        navigate('/community');
      }
    };
    fetchPost();
  }, [postId]);

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

    try {
      setIsSubmitting(true);
      await axiosInstance.patch(`/community/${postId}`, {
        title: form.title,
        content: form.content,
        maxCount: Number(form.maxCount),
        deadline: form.deadline || null,
        tag: form.tag || null,
        techStacks: form.techStacks,
        mode: form.mode,
      });
      navigate(`/community/${postId}`);
    } catch (err) {
      alert(err.response?.data?.message || '수정에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!form) return <div style={{ padding: 40 }}>로딩 중...</div>;

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <h1 className={styles.title}>모집글 수정</h1>
          <p className={styles.subtitle}>스터디 모집글을 수정해요</p>
        </div>

        <div className={styles.form}>
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

          <div className={styles.field}>
            <label className={styles.label}>
              내용 <span className={styles.required}>*</span>
            </label>
            <textarea
              name="content"
              value={form.content}
              onChange={handleChange}
              className={styles.textarea}
              rows={10}
            />
          </div>

          <div className={styles.buttonRow}>
            <button
              type="button"
              onClick={() => navigate(`/community/${postId}`)}
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
              {isSubmitting ? '수정 중...' : '수정 완료'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CommunityEdit;