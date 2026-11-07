import { createElement } from 'react';
import './simulation/nuclear-power.js';

export default function App() {
  return (
    <div className="nuclear-power-view" lang="en">
      {createElement('nuclear-power-console')}
    </div>
  );
}
