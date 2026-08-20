<template>
  <div class="auth">
    <div class="overview">
      <img v-if="backgroundSrc" :src="backgroundSrc" alt="" />
    </div>
    <div class="auth-modal">
      <div class="header__left">
        <img
          v-if="commonInfo?.data?.login?.logo?.path"
          style="max-height: 60px; max-width: 220px; margin: 0 15px 0 22px"
          :src="avatarSrc(commonInfo.data.login.logo.path)"
          alt="河北大学"
        />
        <!--<span style="font-size: 16px;">{{commonInfo.home.title || ''}}</span>-->
        <!--<div style="margin-left: 10px">
          <ChangeLang :isLogin="true" />
        </div>-->
      </div>
      <!--      <div class="container__left">-->
      <!--        {{ commonInfo.login.welcomeText }}-->
      <!--      </div>-->

      <router-view />
    </div>
  </div>
</template>

<script>
import { mapState, mapActions } from 'vuex';
import ChangeLang from '@/components/changeLang.vue';
import { replaceTitle, replaceIcon, avatarSrc } from '@/utils/util';

export default {
  components: { ChangeLang },
  computed: {
    ...mapState('user', ['commonInfo']),
    ...mapState('user', ['lang']),
    backgroundSrc() {
      return avatarSrc(this.commonInfo?.data?.login?.background?.path || '');
    },
  },
  watch: {
    lang: {
      handler(val) {
        if (val) {
          /*this.getImgCode()
          this.getLogoInfo()*/
        }
      },
      immediate: true,
    },
  },
  created() {
    this.getCommonInfo().then(() => {
      replaceTitle(this.$t('header.title'));
      replaceIcon(this.commonInfo?.data?.tab?.logo?.path || '');
    });
  },
  methods: {
    avatarSrc,
    ...mapActions('user', ['getCommonInfo']),
  },
};
</script>

<style lang="scss" scoped>
.overview {
  position: relative;
  height: 100%;
  overflow: hidden;
  z-index: 10;
  background: linear-gradient(to bottom, #f4faff 0%, #dceeff 50%, #c2e0ff 100%);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    background-size: 100% 100%;
  }
}

.auth {
  height: 100%;
}

.auth-modal {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  width: 100%;
  height: 100%;
  z-index: 1000;

  .header__left {
    position: relative;
    width: 100%;
    min-width: 500px;
    color: #fff;
    font-weight: bold;
    display: flex;
    align-items: center;
    margin-top: 16px;
    margin-left: 10px;
    height: 60px;
  }
}
</style>
