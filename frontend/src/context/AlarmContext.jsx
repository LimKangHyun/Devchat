//채팅방 알림 여부를 전역으로 관리
import { createContext, useContext, useState } from "react";

const AlarmContext = createContext();

export const useAlarm = () => useContext(AlarmContext);

export const AlarmProvider = ({ children }) => {
  const [alarmStatusMap, setAlarmStatusMap] = useState({}); // roomId -> enabled

  const updateAlarm = (roomId, enabled) => {
    setAlarmStatusMap(prev => ({ ...prev, [roomId]: enabled }));
  };

  const getAlarmStatus = (roomId) => {
    return alarmStatusMap[roomId];
  };

  return (
    <AlarmContext.Provider value={{ alarmStatusMap, updateAlarm, getAlarmStatus }}>
      {children}
    </AlarmContext.Provider>
  );
};
