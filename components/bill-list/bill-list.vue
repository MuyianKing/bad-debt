<template>
  <view>
    <view v-for="g in groups" :key="g.day" class="bl-group glass">
      <view class="bl-head">
        <text class="bl-title">{{ g.title }}</text>
        <view class="bl-head-right">
          <text v-if="g.expense > 0" class="bl-sum bl-out">支 ¥{{ money(g.expense) }}</text>
          <text v-if="g.income > 0" class="bl-sum bl-in">收 ¥{{ money(g.income) }}</text>
        </view>
      </view>
      <view
        v-for="b in g.bills"
        :key="b.id"
        class="bl-row"
        @tap="$emit('row', b)"
        @longpress="$emit('rowlong', b)"
      >
        <view class="bl-row-left">
          <view class="bl-icon" :style="{ backgroundColor: bgOf(b) }">
            <app-icon :name="iconOf(b.costType)" :color="strokeOf(b)" :size="17" :solid="true"></app-icon>
          </view>
          <view class="bl-name-col">
            <view class="bl-name-row">
              <text class="bl-name">{{ displayName(b) }}</text>
              <text v-if="Number(b.reimb) === 1" class="bl-tag">报销</text>
              <app-icon v-if="Number(b.starred) === 1" name="star" color="#ffb400" :size="14" :stroke-width="2.5"></app-icon>
              <app-icon v-if="imgOf(b)" name="camera" color="#8a90a0" :size="14"></app-icon>
            </view>
            <text v-if="remarkText(b)" class="bl-remark">{{ b.costType }}</text>
          </view>
        </view>
        <text :class="['bl-amount', Number(b.money) < 0 ? 'bl-out' : 'bl-in']">
          {{ Number(b.money) < 0 ? '-' : '+' }}{{ money(Math.abs(Number(b.money))) }}
        </text>
      </view>
    </view>
  </view>
</template>

<script>
import { fmtMoney } from '@/common/fmt.js'
import { iconOf, colorOf, parentOf } from '@/common/cats.js'

export default {
  name: 'bill-list',
  props: {
    groups: { type: Array, default: () => [] }
  },
  emits: ['row', 'rowlong'],
  methods: {
    money(v) {
      return fmtMoney(v)
    },
    iconOf(name) {
      return iconOf(name)
    },
    strokeOf(b) {
      const top = parentOf(b.costType) || b.costType
      return colorOf(top, Number(b.money) < 0 ? 'expense' : 'income')
    },
    bgOf(b) {
      const top = parentOf(b.costType) || b.costType
      const c = colorOf(top, Number(b.money) < 0 ? 'expense' : 'income')
      return this.hexToTint(c)
    },
    hexToTint(hex) {
      const r = parseInt(hex.slice(1, 3), 16)
      const g = parseInt(hex.slice(3, 5), 16)
      const b = parseInt(hex.slice(5, 7), 16)
      const mix = (c) => Math.round(c + (255 - c) * 0.88)
      return 'rgb(' + mix(r) + ',' + mix(g) + ',' + mix(b) + ')'
    },
    displayName(b) {
      const remark = this.remarkText(b)
      if (remark) return remark
      return b.costType || (Number(b.money) < 0 ? '支出' : '收入')
    },
    remarkText(b) {
      const r = String(b.orderRemark || '')
      const idx = r.indexOf('{{img:')
      return (idx >= 0 ? r.slice(0, idx) : r).trim()
    },
    imgOf(b) {
      const r = String(b.orderRemark || '')
      const m = r.match(/\{\{img:([^}]+)\}\}/)
      return m ? m[1] : ''
    }
  }
}
</script>

<style lang="scss">
.bl-group {
  background-color: var(--c-card);
  border-radius: 28rpx;
  padding: 6rpx 28rpx;
  margin: 0 24rpx 24rpx;
  box-shadow: var(--shadow-card);
}
.bl-head {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0 10rpx;
}
.bl-head-right {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.bl-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--c-text);
}
.bl-sum {
  margin-left: 20rpx;
  font-size: 24rpx;
  font-variant-numeric: tabular-nums;
}
.bl-sum.bl-out {
  color: var(--c-out);
}
.bl-sum.bl-in {
  color: var(--c-in);
}
.bl-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  & + .bl-row {
    border-top: 1rpx solid var(--c-line);
  }
}
.bl-row-left {
  display: flex;
  flex-direction: row;
  align-items: center;
  flex: 1;
  min-width: 0;
}
.bl-icon {
  width: 58rpx;
  height: 58rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 22rpx;
  flex-shrink: 0;
}
.bl-icon .app-icon {
  opacity: 0.95;
}
.bl-name-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.bl-name-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.bl-name {
  font-size: 30rpx;
  color: var(--c-text);
  font-weight: 500;
  max-width: 380rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bl-tag {
  margin-left: 12rpx;
  font-size: 20rpx;
  color: var(--c-primary);
  background-color: var(--c-primary-weak);
  border-radius: 8rpx;
  padding: 3rpx 10rpx;
  flex-shrink: 0;
}
.bl-name-row .app-icon {
  margin-left: 10rpx;
  flex-shrink: 0;
}
.bl-remark {
  margin-top: 5rpx;
  font-size: 22rpx;
  color: var(--c-dim);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.bl-amount {
  font-size: 32rpx;
  font-weight: 700;
  margin-left: 16rpx;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.bl-out {
  color: var(--c-out);
}
.bl-in {
  color: var(--c-in);
}
</style>
