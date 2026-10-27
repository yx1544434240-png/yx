const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '..');

function createRuntime(app = { globalData: {} }, wx = {}, request = { get: () => Promise.resolve([]) }, globals = {}) {
  const modules = new Map();
  let definition;
  let component;
  function load(filename) {
    filename = path.resolve(filename);
    if (!path.extname(filename)) filename += '.js';
    if (filename === path.join(root, 'utils', 'request.js') && request !== null) return request;
    const rel = path.relative(root, filename).split(path.sep).join('/');
    const stub = globals.__stubModules && (globals.__stubModules[rel] || globals.__stubModules[path.relative(root, filename)]);
    if (stub) return stub;
    if (modules.has(filename)) return modules.get(filename).exports;
    const module = { exports: {} };
    modules.set(filename, module);
    vm.runInNewContext(fs.readFileSync(filename, 'utf8'), {
      require: name => load(path.resolve(path.dirname(filename), name)), module, exports: module.exports,
      getApp: () => app, wx, console, setTimeout, clearTimeout, setInterval, clearInterval,
      Page: value => { definition = value; }, Component: value => { component = value; }, ...globals
    }, { filename });
    return module.exports;
  }
  function attachUpdates(instance) {
    instance.patches = [];
    instance.setData = (patch, callback) => {
      const copy = structuredClone(patch);
      instance.patches.push(copy);
      for (const [key, value] of Object.entries(copy)) {
        const keys = key.replace(/\[(\d+)\]/g, '.$1').split('.');
        let target = instance.data;
        for (const field of keys.slice(0, -1)) target = target[field];
        target[keys.at(-1)] = value;
      }
      callback?.();
    };
    return instance;
  }
  return {
    app, wx, require: name => load(path.join(root, name)),
    page(name) {
      load(path.join(root, 'pages', name + '.js'));
      return attachUpdates({ ...definition, data: structuredClone(definition.data) });
    },
    component(name, properties) {
      load(path.join(root, 'components', name, 'index.js'));
      const result = attachUpdates({ ...component.methods, data: structuredClone(component.data), properties });
      result.events = [];
      result.triggerEvent = name => result.events.push(name);
      result.lifecycle = (group, name) => component[group][name].call(result);
      result.observe = (name, ...values) => component.observers[name].apply(result, values);
      return result;
    }
  };
}
function deferred() {
  let resolve, reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}
module.exports = { createRuntime, deferred };
