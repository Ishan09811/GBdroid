#include "core_interface.h"

#include <android/log.h>
#include <cstring>
#include <vector>

extern "C" {
#include <mgba/core/core.h>
#include <mgba/core/blip_buf.h>
#include <mgba/core/version.h>
#include <mgba/core/serialize.h>
#include <mgba-util/vfs.h>
}

#define LOG_TAG "core"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class CoreImpl : public CoreInterface {
public:
    bool init() override {
        LOGI("Core::init");
        return true;
    }

    void shutdown() override {
        unloadRom();
    }

    bool loadRom(const uint8_t* data, size_t size) override {
        if (m_core != nullptr) {
            unloadRom();
        }

        m_romBuffer.assign(data, data + size);
        VFile* vf = VFileFromConstMemory(m_romBuffer.data(), m_romBuffer.size());
        if (!vf) {
            LOGE("VFileFromConstMemory failed");
            return false;
        }

        m_core = mCoreFindVF(vf);
        if (!m_core) {
            LOGE("mCoreFindVF failed to identify ROM type");
            vf->close(vf);
            return false;
        }

        if (!m_core->init(m_core)) {
            LOGE("mCore init failed");
            m_core->deinit(m_core);
            m_core = nullptr;
            return false;
        }

        mCoreInitConfig(m_core, nullptr);

        unsigned width, height;
        m_core->desiredVideoDimensions(m_core, &width, &height);
        m_width = static_cast<int>(width);
        m_height = static_cast<int>(height);

        m_videoBuffer.assign(static_cast<size_t>(m_width) * m_height, 0);

        m_core->setVideoBuffer(m_core, m_videoBuffer.data(), static_cast<size_t>(m_width));

        if (!m_core->loadROM(m_core, vf)) {
            LOGE("mCore loadROM failed");
            m_core->deinit(m_core);
            m_core = nullptr;
            return false;
        }

        m_sampleRate = 32768;

        m_core->reset(m_core);

        LOGI("ROM loaded: %dx%d, sampleRate=%d", m_width, m_height, m_sampleRate);
        return true;
    }

    void unloadRom() override {
        if (m_core) {
            m_core->unloadROM(m_core);
            m_core->deinit(m_core);
            m_core = nullptr;
        }
        m_romBuffer.clear();
        m_videoBuffer.clear();
    }

    void reset() override {
        if (m_core) m_core->reset(m_core);
    }

    void runFrame() override {
        if (m_core) m_core->runFrame(m_core);
    }

    const uint32_t* getVideoBuffer() override {
        if (sizeof(color_t) == 4) {
            return reinterpret_cast<const uint32_t*>(m_videoBuffer.data());
        }

        if (m_expandedBuffer.size() != m_videoBuffer.size()) {
            m_expandedBuffer.resize(m_videoBuffer.size());
        }
        const uint16_t* src = reinterpret_cast<const uint16_t*>(m_videoBuffer.data());
        for (size_t i = 0; i < m_videoBuffer.size(); ++i) {
            uint16_t px = src[i];
            uint8_t r5 = (px >> 0) & 0x1F;
            uint8_t g5 = (px >> 5) & 0x1F;
            uint8_t b5 = (px >> 10) & 0x1F;
            uint8_t r8 = (r5 << 3) | (r5 >> 2);
            uint8_t g8 = (g5 << 3) | (g5 >> 2);
            uint8_t b8 = (b5 << 3) | (b5 >> 2);
            m_expandedBuffer[i] = (0xFFu << 24) | (b8 << 16) | (g8 << 8) | r8;
        }
        return m_expandedBuffer.data();
    }

    int getWidth() const override { return m_width; }
    int getHeight() const override { return m_height; }

    void setKeys(uint16_t keyMask) override {
        if (m_core) m_core->setKeys(m_core, static_cast<uint32_t>(keyMask));
    }

    size_t fillAudioBuffer(int16_t* outBuffer, size_t maxFrames) override {
        if (!m_core) {
            std::memset(outBuffer, 0, maxFrames * 2 * sizeof(int16_t));
            return maxFrames;
        }

        blip_t* left = m_core->getAudioChannel(m_core, 0);
        blip_t* right = m_core->getAudioChannel(m_core, 1);
        if (!left || !right) {
            std::memset(outBuffer, 0, maxFrames * 2 * sizeof(int16_t));
            return maxFrames;
        }

        size_t available = blip_samples_avail(left);
        size_t framesToRead = available < maxFrames ? available : maxFrames;
        if (framesToRead == 0) return 0;

        std::vector<int16_t> leftBuf(framesToRead);
        std::vector<int16_t> rightBuf(framesToRead);
        blip_read_samples(left, leftBuf.data(), static_cast<int>(framesToRead), 0);
        blip_read_samples(right, rightBuf.data(), static_cast<int>(framesToRead), 0);

        for (size_t i = 0; i < framesToRead; ++i) {
            outBuffer[i * 2] = leftBuf[i];
            outBuffer[i * 2 + 1] = rightBuf[i];
        }

        return framesToRead;
    }

    int getAudioSampleRate() const override {
        return m_sampleRate > 0 ? m_sampleRate : 32768;
    }

    bool saveState(uint8_t* outBuffer, size_t bufferSize, size_t* outWritten) override {
        if (!m_core) { *outWritten = 0; return false; }
        VFile* vf = VFileFromMemory(outBuffer, bufferSize);
        if (!vf) { *outWritten = 0; return false; }

        bool ok = mCoreSaveStateNamed(m_core, vf, SAVESTATE_SAVEDATA | SAVESTATE_SCREENSHOT);
        *outWritten = ok ? static_cast<size_t>(vf->seek(vf, 0, SEEK_CUR)) : 0;
        vf->close(vf);
        return ok;
    }

    bool loadState(const uint8_t* data, size_t size) override {
        if (!m_core) return false;
        VFile* vf = VFileFromConstMemory(data, size);
        if (!vf) return false;
        bool ok = mCoreLoadStateNamed(m_core, vf, SAVESTATE_SAVEDATA | SAVESTATE_SCREENSHOT);
        vf->close(vf);
        return ok;
    }

    bool loadSaveData(const uint8_t* data, size_t size) override {
        if (!m_core) return false;
        bool ok = m_core->savedataRestore(m_core, data, size, true);
        if (!ok) {
            LOGE("savedataRestore failed (size=%zu)", size);
        }
        return ok;
    }

    std::vector<uint8_t> exportSaveData() override {
        if (!m_core) return {};
        void* sram = nullptr;
        size_t size = m_core->savedataClone(m_core, &sram);
        if (size == 0 || sram == nullptr) {
            return {};
        }

        std::vector<uint8_t> result(static_cast<uint8_t*>(sram), static_cast<uint8_t*>(sram) + size);
        free(sram);
        return result;
    }

    std::string getGameTitle() override {
        if (!m_core) return "";
        char titleBuf[16] = {0};
        m_core->getGameTitle(m_core, titleBuf);
        titleBuf[15] = '\0';
        return std::string(titleBuf);
    }

    std::string getGameCode() override {
        if (!m_core) return "";
        char codeBuf[16] = {0};
        m_core->getGameCode(m_core, codeBuf);
        codeBuf[15] = '\0';
        return std::string(codeBuf);
    }

    int getPlatform() override {
        if (!m_core) return -1;
        return static_cast<int>(m_core->platform(m_core));
    }

private:
    mCore* m_core = nullptr;
    std::vector<uint8_t> m_romBuffer;
    std::vector<uint32_t> m_videoBuffer;
    // only populated and used when color_t is 16-bit; empty and unused otherwise.
    std::vector<uint32_t> m_expandedBuffer;
    int m_width = 0;
    int m_height = 0;
    int m_sampleRate = 0;
};

CoreInterface* createCore() {
    return new CoreImpl();
}
