import { useState, useCallback } from "react";

export function useNotification() {
  const [notice, setNotice] = useState(null);

  const notify = useCallback((message, type = "success") => {
    setNotice({ message, type });
    window.setTimeout(() => setNotice(null), 3200);
  }, []);

  return { notice, notify };
}
