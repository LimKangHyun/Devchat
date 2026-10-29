import React, { useState } from 'react';
import s from './Schedule.module.css';

// 프로필 사진, 없거나 안 열리면 이름 첫 글자
const Avatar = ({ name, img, size = 28 }) => {
  const [broken, setBroken] = useState(false);
  const style = { width: size, height: size, fontSize: Math.round(size * 0.45) };
  if (img && !broken) {
    return (
      <img
        className={s.avatar}
        style={style}
        src={`${process.env.REACT_APP_PROFILE_IMAGE_URL}/${img}`}
        alt=""
        onError={() => setBroken(true)}
      />
    );
  }
  return <span className={s.avatar} style={style} aria-hidden="true">{(name || '?').trim().slice(0, 1).toUpperCase()}</span>;
};

export default Avatar;
