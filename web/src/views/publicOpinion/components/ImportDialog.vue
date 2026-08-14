<template>
  <el-dialog
    :title="$t('publicOpinion.import.title')"
    :visible.sync="visible"
    :close-on-click-modal="false"
    width="680px"
    class="public-opinion-import-dialog"
    :before-close="handleClose"
  >
    <div class="import-description">
      <p>{{ $t('publicOpinion.import.supportedFormats') }}</p>
      <p>{{ $t('publicOpinion.import.maxFileSize') }}</p>
      <p>{{ $t('publicOpinion.import.maxRows') }}</p>
      <p>{{ $t('publicOpinion.import.templateTip') }}</p>
    </div>

    <el-upload
      ref="upload"
      class="file-uploader"
      drag
      action="#"
      accept=".xlsx,.csv"
      :auto-upload="false"
      :limit="1"
      :file-list="fileList"
      :on-change="handleFileChange"
      :on-remove="handleFileRemove"
      :on-exceed="handleFileExceed"
    >
      <i class="el-icon-upload" />
      <div class="el-upload__text">
        {{ $t('publicOpinion.import.selectFile') }}
      </div>
      <div slot="tip" class="el-upload__tip">
        {{ $t('publicOpinion.import.fileLimitTip') }}
      </div>
    </el-upload>

    <section v-if="result" class="import-result">
      <h3>{{ $t('publicOpinion.import.completed') }}</h3>
      <div class="result-summary">
        <div v-for="item in resultSummary" :key="item.key" class="summary-item">
          <span>{{ item.label }}</span>
          <strong :class="item.key">{{ item.value }}</strong>
        </div>
      </div>

      <div v-if="errorDetails.length" class="error-details">
        <h4>{{ $t('publicOpinion.import.errorDetails') }}</h4>
        <el-table :data="displayErrorDetails" size="mini" max-height="240">
          <el-table-column
            prop="row"
            :label="$t('publicOpinion.import.row')"
            width="80"
          />
          <el-table-column
            prop="field"
            :label="$t('publicOpinion.import.field')"
            width="130"
          />
          <el-table-column
            prop="reason"
            :label="$t('publicOpinion.import.reason')"
            min-width="280"
          />
        </el-table>
        <p
          v-if="errorDetails.length > maxVisibleErrors"
          class="error-limit-tip"
        >
          {{
            $t('publicOpinion.import.errorLimitTip', {
              count: maxVisibleErrors,
            })
          }}
        </p>
      </div>
    </section>

    <span slot="footer" class="dialog-footer">
      <el-button :disabled="importing" @click="handleClose">
        {{ $t('common.confirm.cancel') }}
      </el-button>
      <el-button type="primary" :loading="importing" @click="handleImport">
        {{ $t('publicOpinion.import.start') }}
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
import { importPublicOpinion } from '@/api/publicOpinion';

const MAX_FILE_SIZE = 5 * 1024 * 1024;
const MAX_VISIBLE_ERRORS = 100;

export default {
  name: 'PublicOpinionImportDialog',
  data() {
    return {
      visible: false,
      importing: false,
      selectedFile: null,
      fileList: [],
      result: null,
      errorDetails: [],
      maxVisibleErrors: MAX_VISIBLE_ERRORS,
    };
  },
  computed: {
    resultSummary() {
      if (!this.result) return [];
      return [
        {
          key: 'total',
          label: this.$t('publicOpinion.import.total'),
          value: this.result.totalRows || 0,
        },
        {
          key: 'success',
          label: this.$t('publicOpinion.import.success'),
          value: this.result.successRows || 0,
        },
        {
          key: 'duplicate',
          label: this.$t('publicOpinion.import.duplicate'),
          value: this.result.duplicateRows || 0,
        },
        {
          key: 'failed',
          label: this.$t('publicOpinion.import.failed'),
          value: this.result.failedRows || 0,
        },
      ];
    },
    displayErrorDetails() {
      return this.errorDetails.slice(0, this.maxVisibleErrors);
    },
  },
  methods: {
    open() {
      this.reset();
      this.visible = true;
    },
    reset() {
      this.selectedFile = null;
      this.fileList = [];
      this.result = null;
      this.errorDetails = [];
      this.$nextTick(() => this.$refs.upload?.clearFiles());
    },
    handleClose() {
      if (this.importing) return;
      this.visible = false;
      this.reset();
    },
    handleFileChange(file, fileList) {
      const rawFile = file.raw;
      if (!this.validateFile(rawFile)) {
        this.selectedFile = null;
        this.fileList = [];
        this.$nextTick(() => this.$refs.upload?.clearFiles());
        return;
      }
      this.selectedFile = rawFile;
      this.fileList = fileList.slice(-1);
      this.result = null;
      this.errorDetails = [];
    },
    handleFileRemove() {
      this.selectedFile = null;
      this.fileList = [];
    },
    handleFileExceed() {
      this.$message.warning(this.$t('publicOpinion.import.singleFileOnly'));
    },
    validateFile(file) {
      if (!file) {
        this.$message.warning(this.$t('publicOpinion.import.noFile'));
        return false;
      }
      const fileName = String(file.name || '').toLowerCase();
      const extension = fileName.slice(fileName.lastIndexOf('.'));
      if (!['.xlsx', '.csv'].includes(extension)) {
        this.$message.error(this.$t('publicOpinion.import.invalidFormat'));
        return false;
      }
      if (!file.size) {
        this.$message.error(this.$t('publicOpinion.import.emptyFile'));
        return false;
      }
      if (file.size > MAX_FILE_SIZE) {
        this.$message.error(this.$t('publicOpinion.import.fileTooLarge'));
        return false;
      }
      return true;
    },
    parseErrorDetails(value) {
      if (Array.isArray(value)) return value;
      if (!value) return [];
      try {
        const details = JSON.parse(value);
        return Array.isArray(details) ? details : [];
      } catch (error) {
        return [{ row: '-', field: '-', reason: String(value) }];
      }
    },
    async handleImport() {
      if (!this.validateFile(this.selectedFile)) return;
      this.importing = true;
      try {
        const response = await importPublicOpinion(this.selectedFile);
        if (response.code !== 0 || !response.data) return;
        this.result = response.data;
        this.errorDetails = this.parseErrorDetails(response.data.errorDetail);
        const status = response.data.status;
        if (status === 'success') {
          this.$message.success(this.$t('publicOpinion.import.successMessage'));
          this.$emit('import-success');
        } else if (status === 'partial_success') {
          this.$message.warning(this.$t('publicOpinion.import.partialMessage'));
          this.$emit('import-success');
        } else if (status === 'failed') {
          this.$message.error(this.$t('publicOpinion.import.failedMessage'));
        }
      } finally {
        this.importing = false;
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.public-opinion-import-dialog {
  ::v-deep .el-dialog__body {
    padding-top: 16px;
  }
}

.import-description {
  margin-bottom: 16px;
  padding: 12px 16px;
  color: #687083;
  background: #f7f8fa;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.7;

  p {
    margin: 0;
  }
}

.file-uploader {
  ::v-deep .el-upload,
  ::v-deep .el-upload-dragger {
    width: 100%;
  }
}

.import-result {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid #edf0f5;

  h3,
  h4 {
    margin: 0 0 12px;
    color: $color_title;
  }

  h3 {
    font-size: 15px;
  }

  h4 {
    font-size: 13px;
  }
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin-bottom: 18px;
  gap: 10px;
}

.summary-item {
  padding: 12px;
  text-align: center;
  background: #f7f8fa;
  border-radius: 6px;

  span {
    display: block;
    margin-bottom: 4px;
    color: #8b91a1;
    font-size: 12px;
  }

  strong {
    color: $color_title;
    font-size: 20px;

    &.success {
      color: #67c23a;
    }

    &.duplicate {
      color: #e6a23c;
    }

    &.failed {
      color: #f56c6c;
    }
  }
}

.error-limit-tip {
  margin: 8px 0 0;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 760px) {
  .result-summary {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
