/// <reference types="node" />
// @vitest-environment node
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { runInNewContext } from 'node:vm'
import { describe, expect, it } from 'vitest'

const directory = resolve(process.cwd(), '../automation')
const workflow = JSON.parse(readFileSync(resolve(directory,
  readdirSync(directory).find(name => name.endsWith('Qualification.json'))!), 'utf8'))
const node = (name: string) => workflow.nodes.find((item: { name: string }) => item.name === name)
const correlation = { leadId: 'trusted-lead', attemptId: 'trusted-attempt', workflowExecutionId: 'accepted-execution' }
const result = { qualificationScore: 80, priority: 'HIGH', category: 'AUTOMATION', aiSummary: 'Summary', recommendedReply: 'Reply' }
const lookup = (name: string) => {
  expect(name).toBe('Validate and Normalize Dispatch')
  return { first: () => ({ json: correlation }) }
}
const evaluate = (expression: string, data: object) => runInNewContext(`(${expression})`, { $: lookup, $json: data })
const url = (template: string, data: object) => template.replace(/^=/, '').replace(/{{(.*?)}}/g,
  (_: string, expression: string) => evaluate(expression, data))

describe('committed qualification workflow trust boundary', () => {
  it.each([
    { leadId: 'foreign-lead' }, { attemptId: 'foreign-attempt' },
    { workflowExecutionId: 'foreign-execution' }, { workspaceId: 'foreign-workspace' },
    { outboxId: 'foreign-outbox' }, { callbackUrl: 'https://invalid.example', callbackPath: '/foreign' },
    { correlation: { ...correlation, leadId: 'foreign-lead' }, nested: { workspaceId: 'foreign-workspace' } },
    { status: 'SUCCEEDED', authentication: 'none', __proto__: { leadId: 'foreign-lead' } },
  ])('projects business fields without granting authority to extra fields %#', extra => {
    const parsed = runInNewContext(`(function(){${node('Parse and Validate Qualification').parameters.jsCode}})()`, {
      $: lookup, $json: { content: { parts: [{ text: JSON.stringify({ ...result, ...extra }) }] } },
    })[0].json
    expect(parsed).toEqual(result)
    for (const [name, terminal] of [['Report Success', 'success'], ['Report Failure', 'failure']]) {
      const callback = node(name)
      expect(url(callback.parameters.url, parsed)).toBe(
        `http://backend:8080/api/v1/automation/leads/trusted-lead/qualification-attempts/trusted-attempt/${terminal}`)
      const body = evaluate(callback.parameters.jsonBody.slice(3, -2), parsed)
      expect(body.workflowExecutionId).toBe(correlation.workflowExecutionId)
      expect(callback.parameters.authentication).toBe('predefinedCredentialType')
      expect(callback.parameters.nodeCredentialType).toBe('httpHeaderAuth')
      expect(callback.credentials.httpHeaderAuth).toBeDefined()
    }
  })
  it.each([null, [], { ...result, qualificationScore: 101 }, { ...result, priority: 'OTHER' }])(
    'rejects invalid model business data %#', payload => {
      expect(() => runInNewContext(`(function(){${node('Parse and Validate Qualification').parameters.jsCode}})()`, {
        $: lookup, $json: { content: { parts: [{ text: JSON.stringify(payload) }] } },
      })).toThrow('Invalid AI response')
    })
})
