import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(
  new URL('../../../../../package.json', import.meta.url),
);

const timeline = JSON.parse(
  fs.readFileSync(new URL('../build/timeline.json', import.meta.url), 'utf8'),
);
const component = fs.readFileSync(
  new URL('../../index.vue', import.meta.url),
  'utf8',
);

assert.equal(timeline.initialState, 'idle');
assert.equal(
  new Set(timeline.states.map(({ id }) => id)).size,
  timeline.states.length,
);
for (const segment of timeline.segments) {
  assert(segment.start <= segment.hold && segment.hold < segment.endExclusive);
}
assert.equal(timeline.segments.at(-1).endExclusive, 9.6);
assert(
  !component.includes('pointermove') && !component.includes('followPointer'),
);
assert(component.includes('digital-eye-blink'));
assert(component.includes('digital-eye-gaze'));
assert(component.includes('digital-idle-mouth'));
assert(component.includes('prefers-reduced-motion'));
const compiler = require('vue-template-compiler');
const sfc = compiler.parseComponent(component);
assert.deepEqual(compiler.compile(sfc.template.content).errors, []);
const css = require('sass').compileString(sfc.styles[0].content).css;
const eyeRect = component.match(
  /<rect\s+class="digital-human__eye-shape"([^>]+)>/,
)[1];
assert.equal(
  eyeRect.match(/\bwidth="([^"]+)"/)[1],
  eyeRect.match(/\bheight="([^"]+)"/)[1],
  'Resting eyes must stay square',
);
assert.match(
  css,
  /\.digital-human__eye-shape \{\s*fill: none;/,
  'Eyes must be hollow',
);
assert(
  component.includes('digital-human__eye-core'),
  'Keep the inner light core',
);
const blinkDuration = Number(
  css.match(/animation: digital-eye-blink ([\d.]+)s/)[1],
);
assert(blinkDuration >= 3 && blinkDuration <= 5, 'Blink every 3–5 seconds');
assert(!sfc.template.content.includes('<b>'), 'No solid pupils on the face');
for (const animation of [
  'digital-float',
  'digital-wave',
  'digital-antenna',
  'digital-shadow',
]) {
  assert(css.includes(`@keyframes ${animation}`));
}
assert(css.includes('mask-composite: exclude'));
assert(css.includes('@supports not (mask-composite: exclude)'));
assert(css.includes('font-size: 72px'));
assert(/document\.removeEventListener\(\s*'visibilitychange'/.test(component));
assert(component.includes('digital-human__fallback'));
assert(component.includes('@click="setExpression(\'happy\', 1500)"'));

console.log('oil-motion QA passed');
