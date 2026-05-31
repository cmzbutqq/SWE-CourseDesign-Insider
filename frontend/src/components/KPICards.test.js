import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import KPICards from './KPICards.vue';

const baseOverview = {
  nodes: {
    total: 3,
    online: 1,
    offline: 0,
    warning: 2
  },
  services: {
    total: 5,
    healthy: 5,
    abnormal: 0
  },
  unresolvedAlerts: 2
};

describe('KPICards', () => {
  it('does not describe warning nodes as all online', () => {
    const wrapper = mount(KPICards, {
      props: {
        data: baseOverview
      }
    });

    const onlineCardText = wrapper.findAll('.kpi-card')[0].text();

    expect(onlineCardText).toContain('2 告警');
    expect(onlineCardText).not.toContain('全部在线');
  });

  it('uses generic alert copy for warning nodes instead of high load only', () => {
    const wrapper = mount(KPICards, {
      props: {
        data: baseOverview
      }
    });

    const warningCardText = wrapper.findAll('.kpi-card')[1].text();

    expect(warningCardText).toContain('存在告警');
    expect(warningCardText).not.toContain('存在高负载');
  });
});
