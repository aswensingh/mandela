import { useMemo } from 'react';
import Particles, {
  ParticlesProvider,
  useParticlesProvider,
} from '@tsparticles/react';
import type { Engine, ISourceOptions } from '@tsparticles/engine';
import { loadSlim } from '@tsparticles/slim';

/**
 * Animated background of slow-moving connected dots, rendered behind the login card.
 *
 * @tsparticles/react v4 uses a Provider pattern: ParticlesProvider initializes the
 * engine once (here we load only the "slim" preset — full bundle is ~10x larger),
 * and consumers render <Particles options={...}/> as children. The inner component
 * subscribes via useParticlesProvider() and waits until the engine reports ready,
 * avoiding a flash of unstyled background on slow connections.
 *
 * - AntD primary-blue tone (#1677ff) at low opacity so it doesn't fight the form.
 * - `position: fixed; inset: 0` so it covers the entire viewport regardless of scroll.
 * - `pointer-events: none` so clicks pass through to the form.
 */
const initEngine = async (engine: Engine): Promise<void> => {
  await loadSlim(engine);
};

export default function LoginParticles() {
  return (
    <ParticlesProvider init={initEngine}>
      <ParticlesCanvas />
    </ParticlesProvider>
  );
}

function ParticlesCanvas() {
  const { loaded } = useParticlesProvider();

  const options = useMemo<ISourceOptions>(
    () => ({
      // Transparent canvas — the gradient lives on the LoginPage's Layout
      // element so the particles render directly over it.
      background: { color: { value: 'transparent' } },
      fullScreen: { enable: false },
      fpsLimit: 60,
      detectRetina: true,
      interactivity: {
        events: {
          // Light hover effect: nearby links highlight as the cursor moves through them.
          // tsparticles disables interactivity on touch automatically.
          onHover: { enable: true, mode: 'grab' },
          resize: { enable: true },
        },
        modes: {
          grab: { distance: 160, links: { opacity: 0.9 } },
        },
      },
      particles: {
        // White dots on the blue gradient — bright enough to read against any of
        // the gradient stops (navy → bright blue) without retuning per-stop.
        color: { value: '#ffffff' },
        links: {
          color: '#ffffff',
          distance: 140,
          enable: true,
          opacity: 0.4,
          width: 1,
        },
        move: {
          enable: true,
          direction: 'none',
          outModes: { default: 'bounce' },
          random: false,
          speed: 0.7, // slow + meditative; faster gets distracting
          straight: false,
        },
        number: {
          density: { enable: true, width: 1920, height: 1080 },
          value: 80,
        },
        // Subtle pulse so the field has life even when the cursor isn't on it.
        opacity: {
          value: { min: 0.5, max: 0.9 },
          animation: { enable: true, speed: 0.4, sync: false },
        },
        shape: { type: 'circle' },
        size: { value: { min: 1.5, max: 3.5 } },
      },
    }),
    [],
  );

  if (!loaded) return null;

  return (
    <Particles
      id="login-particles"
      options={options}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 0,
        pointerEvents: 'none',
      }}
    />
  );
}
