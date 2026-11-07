import { useEffect, useState } from 'react';
import { motion } from 'motion/react';

// Placeholder shell. Screens follow the design canvas "IAM Monthly Meeting Hub".
export default function App() {
  const [health, setHealth] = useState('checking');

  useEffect(() => {
    fetch('/api/health')
      .then((r) => r.json())
      .then((d) => setHealth(d.status))
      .catch(() => setHealth('unreachable'));
  }, []);

  return (
    <main className="shell">
      <motion.h1 initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3 }}>
        IAM Monthly Meeting
      </motion.h1>
      <p>API: {health}</p>
    </main>
  );
}
