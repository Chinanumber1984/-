import test from 'node:test'
import assert from 'node:assert/strict'
import {portalCapabilities} from './portalCapabilities.mjs'

test('portal roles see only their assigned business sections', () => {
  assert.deepEqual(portalCapabilities(['QUOTE']), {sourcing:true,collaboration:false,shipping:false,settlements:false,quality:false,performance:true})
  assert.deepEqual(portalCapabilities(['SHIP']), {sourcing:false,collaboration:true,shipping:true,settlements:false,quality:false,performance:false})
  assert.deepEqual(portalCapabilities(['QUALITY']), {sourcing:false,collaboration:false,shipping:false,settlements:false,quality:true,performance:true})
  assert.deepEqual(portalCapabilities(['FINANCE']), {sourcing:false,collaboration:true,shipping:false,settlements:true,quality:false,performance:true})
})

test('administrator and combined roles receive the union of capabilities', () => {
  assert.ok(Object.values(portalCapabilities(['ADMIN'])).every(Boolean))
  assert.deepEqual(portalCapabilities(['SHIP','FINANCE']), {sourcing:false,collaboration:true,shipping:true,settlements:true,quality:false,performance:true})
})

test('unknown or missing roles fail closed', () => {
  assert.ok(Object.values(portalCapabilities(['UNRECOGNIZED'])).every(value => !value))
  assert.ok(Object.values(portalCapabilities()).every(value => !value))
})
