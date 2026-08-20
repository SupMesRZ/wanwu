<template>
  <div class="auth-box">
    <p class="auth-header">
      <span style="font-weight: bold">{{ $t('login.title') }}</span>
    </p>
    <div class="auth-form">
      <el-form ref="form" :model="form" label-position="top">
        <el-form-item class="auth-form-item">
          <img alt="" class="auth-icon" src="@/assets/imgs/user.png" />
          <el-input
            v-model.trim="form.username"
            :placeholder="
              $t('common.input.placeholder') + $t('login.form.username')
            "
          />
        </el-form-item>
        <el-form-item class="auth-form-item">
          <img alt="" class="auth-icon" src="@/assets/imgs/pwd.png" />
          <el-input
            v-model.trim="form.password"
            :placeholder="
              $t('common.input.placeholder') + $t('login.form.password')
            "
            :type="isShowPwd ? '' : 'password'"
            class="auth-pwd-input"
          />
          <img
            v-if="!isShowPwd"
            alt=""
            class="pwd-icon"
            src="@/assets/imgs/hidePwd.png"
            @click="isShowPwd = true"
          />
          <img
            v-else
            alt=""
            class="pwd-icon"
            src="@/assets/imgs/showPwd.png"
            @click="isShowPwd = false"
          />
        </el-form-item>
        <el-form-item class="auth-form-item">
          <img alt="" class="auth-icon" src="@/assets/imgs/code.png" />
          <el-input
            v-model.trim="form.code"
            :placeholder="
              $t('common.input.placeholder') + $t('login.form.code')
            "
            style="width: calc(100% - 90px)"
            @keyup.enter.native="addByEnterKey"
          />
          <span
            style="
              display: inline-block;
              height: 32px;
              width: 80px;
              margin-left: 10px;
              vertical-align: middle;
            "
          >
            <img
              v-if="codeData.b64"
              :src="codeData.b64"
              style="width: 100%; height: 100%"
              @click="getImgCode"
            />
          </span>
        </el-form-item>
      </el-form>
      <div class="nav-bt">
        <span v-if="commonInfo?.data?.register?.email?.status">
          {{ $t('login.askAccount') }}
          <span
            :style="{ color: 'var(--color)', cursor: 'pointer' }"
            @click="$router.push({ path: `/register` })"
          >
            {{ $t('login.register') }}
          </span>
        </span>
        <span
          v-if="commonInfo?.data?.resetPassword?.email?.status"
          :style="{
            color: 'var(--color)',
            cursor: 'pointer',
            float: 'right',
          }"
          @click="$router.push({ path: `/reset` })"
        >
          {{ $t('login.forgetPassword') }}
        </span>
      </div>
      <div class="auth-bt">
        <p
          :class="['primary-bt', { disabled: isDisabled() }]"
          :style="`background: ${commonInfo?.data?.login?.loginButtonColor} !important`"
          @click="doLogin"
        >
          {{ $t('login.button') }}
        </p>
      </div>
      <div class="bottom-text">
        {{ $t('brand.description') }}
      </div>
    </div>
    <dialog2FA ref="dialog2FA"></dialog2FA>
  </div>
</template>

<script>
import dialog2FA from './2FADialog';
import { mapActions, mapMutations, mapState } from 'vuex';
import { getImgVerCode } from '@/api/user';
import { rsaEncrypt } from '@/utils/crypto';
import { redirectUrl } from '@/utils/util';

export default {
  components: { dialog2FA },
  data() {
    return {
      form: {
        username: '',
        password: '',
        code: '',
      },
      isShowPwd: false,
      codeData: {
        key: '',
        b64: '',
      },
      params: {
        client_id: '',
        redirect_uri: '',
        scope: '',
        response_type: '',
        state: '',
        client_name: '',
      },
    };
  },
  created() {
    // 如果token过期，清空token
    if (
      localStorage.getItem('access_cert') &&
      this.$store.state.user.expiresAt <= Date.now()
    ) {
      this.setToken('');
    }
    // 如果已登录，重定向到有权限的页面
    // if (this.$store.state.user.token && localStorage.getItem("access_cert") && !this.$store.state.user.is2FA) redirectUrl()

    this.getImgCode();
  },
  computed: {
    ...mapState('user', ['commonInfo']),
  },
  watch: {
    $route: {
      handler() {
        this.params = this.$route.query;
        if (
          this.$store.state.user.token &&
          localStorage.getItem('access_cert') &&
          !this.$store.state.user.is2FA &&
          this.params.client_id
        )
          this.$router.push({
            path: '/oauth',
            query: this.params,
          });
      },
      // 深度观察监听
      deep: true,
    },
  },
  mounted() {
    this.params = this.$route.query;
    if (
      this.$store.state.user.token &&
      localStorage.getItem('access_cert') &&
      !this.$store.state.user.is2FA &&
      this.params.client_id
    )
      this.$router.push({
        path: '/oauth',
        query: this.params,
      });
  },
  methods: {
    ...mapActions('user', ['LoginIn', 'LoginIn2FA1']),
    ...mapMutations('user', ['setToken']),
    isDisabled() {
      const { username, password, code } = this.form;
      return !(username && password && code);
    },
    addByEnterKey(e) {
      if (e.keyCode === 13) {
        this.doLogin();
      }
    },
    // 获取图片验证码
    async getImgCode() {
      const res = await getImgVerCode();
      this.codeData = res.data || {};
    },
    async doLogin() {
      if (this.isDisabled()) return;

      const { cipher, keyId } = await rsaEncrypt(this.form.password);
      const data = {
        username: this.form.username,
        cipher,
        keyId,
        key: this.codeData.key,
        code: this.form.code,
      };

      try {
        if (this.commonInfo?.data?.loginEmail?.email?.status) {
          const { isEmailCheck, isUpdatePassword } =
            await this.LoginIn2FA1(data);
          this.$refs.dialog2FA.showDialog(
            isEmailCheck,
            isUpdatePassword,
            this.params,
          );
        } else await this.LoginIn({ loginInfo: data, params: this.params });
      } catch (e) {
        await this.getImgCode();
      }
    },
  },
};
</script>

<style lang="scss" scoped>
@import '@/style/auth.scss';
</style>
