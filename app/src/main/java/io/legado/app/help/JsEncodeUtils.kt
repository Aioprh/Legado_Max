package io.legado.app.help

import android.util.Base64
import android.webkit.JavascriptInterface
import cn.hutool.core.util.HexUtil
import cn.hutool.crypto.digest.DigestUtil
import cn.hutool.crypto.digest.HMac
import cn.hutool.crypto.symmetric.SymmetricCrypto
import io.legado.app.constant.AppLog
import io.legado.app.help.crypto.AsymmetricCrypto
import io.legado.app.help.crypto.Sign
import io.legado.app.help.crypto.SymmetricCryptoAndroid
import io.legado.app.utils.MD5Utils
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


/**
 * js加解密扩展类, 在js中通过java变量调用
 * 添加方法，请更新文档/legado/app/src/main/assets/help/JsHelp.md
 */
@Suppress("unused")
interface JsEncodeUtils {

    @JavascriptInterface
    fun md5Encode(str: String): String {
        return MD5Utils.md5Encode(str)
    }

    @JavascriptInterface
    fun md5Encode16(str: String): String {
        return MD5Utils.md5Encode16(str)
    }


    //******************对称加密解密************************//

    /**
     * 在js中这样使用
     * java.createSymmetricCrypto(transformation, key, iv).decrypt(data)
     * java.createSymmetricCrypto(transformation, key, iv).decryptStr(data)

     * java.createSymmetricCrypto(transformation, key, iv).encrypt(data)
     * java.createSymmetricCrypto(transformation, key, iv).encryptBase64(data)
     * java.createSymmetricCrypto(transformation, key, iv).encryptHex(data)
     */

    /* 调用SymmetricCrypto key为null时使用随机密钥*/
    fun createSymmetricCrypto(
        transformation: String,
        key: ByteArray?,
        iv: ByteArray?
    ): SymmetricCrypto {
        val symmetricCrypto = SymmetricCryptoAndroid(transformation, key)
        return if (iv != null && iv.isNotEmpty()) symmetricCrypto.setIv(iv) else symmetricCrypto
    }

    fun createSymmetricCrypto(
        transformation: String,
        key: ByteArray
    ): SymmetricCrypto {
        return createSymmetricCrypto(transformation, key, null)
    }

    fun createSymmetricCrypto(
        transformation: String,
        key: String
    ): SymmetricCrypto {
        return createSymmetricCrypto(transformation, key, null)
    }

    fun createSymmetricCrypto(
        transformation: String,
        key: String,
        iv: String?
    ): SymmetricCrypto {
        return createSymmetricCrypto(
            transformation, key.encodeToByteArray(), iv?.encodeToByteArray()
        )
    }
    //******************非对称加密解密************************//

    /* keys都为null时使用随机密钥 */
    fun createAsymmetricCrypto(
        transformation: String
    ): AsymmetricCrypto {
        return AsymmetricCrypto(transformation)
    }

    //******************签名************************//
    fun createSign(
        algorithm: String
    ): Sign {
        return Sign(algorithm)
    }
    //******************对称加密解密old************************//

    /////AES
    /**
     * AES 解码为 ByteArray
     * @param str 传入的AES加密的数据
     * @param key AES 解密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decrypt(str)")
    )
    fun aesDecodeToByteArray(
        str: String, key: String, transformation: String, iv: String
    ): ByteArray? {
        return createSymmetricCrypto(transformation, key, iv).decrypt(str)
    }

    /**
     * AES 解码为 String
     * @param str 传入的AES加密的数据
     * @param key AES 解密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(str)")
    )
    @JavascriptInterface
    fun aesDecodeToString(
        str: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).decryptStr(str)
    }

    /**
     * AES解码为String，算法参数经过Base64加密
     *
     * @param data 加密的字符串
     * @param key Base64后的密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv Base64后的加盐
     * @return 解密后的字符串
     */
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun aesDecodeArgsBase64Str(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto(
            "AES/${mode}/${padding}",
            Base64.decode(key, Base64.NO_WRAP),
            Base64.decode(iv, Base64.NO_WRAP)
        ).decryptStr(data)
    }

    /**
     * 已经base64的AES 解码为 ByteArray
     * @param str 传入的AES Base64加密的数据
     * @param key AES 解密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decrypt(str)")
    )
    fun aesBase64DecodeToByteArray(
        str: String, key: String, transformation: String, iv: String
    ): ByteArray? {
        return createSymmetricCrypto(transformation, key, iv).decrypt(str)
    }

    /**
     * 已经base64的AES 解码为 String
     * @param str 传入的AES Base64加密的数据
     * @param key AES 解密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(str)")
    )
    @JavascriptInterface
    fun aesBase64DecodeToString(
        str: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).decryptStr(str)
    }

    /**
     * 加密aes为ByteArray
     * @param data 传入的原始数据
     * @param key AES加密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decrypt(data)")
    )
    fun aesEncodeToByteArray(
        data: String, key: String, transformation: String, iv: String
    ): ByteArray? {
        return createSymmetricCrypto(transformation, key, iv).encrypt(data)
    }

    /**
     * 加密aes为String
     * @param data 传入的原始数据
     * @param key AES加密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun aesEncodeToString(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).decryptStr(data)
    }

    /**
     * 加密aes后Base64化的ByteArray
     * @param data 传入的原始数据
     * @param key AES加密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data).toByteArray()")
    )
    fun aesEncodeToBase64ByteArray(
        data: String, key: String, transformation: String, iv: String
    ): ByteArray? {
        return createSymmetricCrypto(transformation, key, iv).encryptBase64(data).toByteArray()
    }

    /**
     * 加密aes后Base64化的String
     * @param data 传入的原始数据
     * @param key AES加密的key
     * @param transformation AES加密的方式
     * @param iv ECB模式的偏移向量
     */
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data)")
    )
    @JavascriptInterface
    fun aesEncodeToBase64String(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).encryptBase64(data)
    }


    /**
     * AES加密并转为Base64，算法参数经过Base64加密
     *
     * @param data 被加密的字符串
     * @param key Base64后的密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv Base64后的加盐
     * @return 加密后的Base64
     */
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data)")
    )
    @JavascriptInterface
    fun aesEncodeArgsBase64Str(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto("AES/${mode}/${padding}", key, iv).encryptBase64(data)
    }

    /////DES
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun desDecodeToString(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).decryptStr(data)
    }

    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun desBase64DecodeToString(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).decryptStr(data)
    }

    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encrypt(data)")
    )
    @JavascriptInterface
    fun desEncodeToString(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return String(createSymmetricCrypto(transformation, key, iv).encrypt(data))
    }

    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data)")
    )
    @JavascriptInterface
    fun desEncodeToBase64String(
        data: String, key: String, transformation: String, iv: String
    ): String? {
        return createSymmetricCrypto(transformation, key, iv).encryptBase64(data)
    }

    //////3DES
    /**
     * 3DES解密
     *
     * @param data 加密的字符串
     * @param key 密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv 加盐
     * @return 解密后的字符串
     */
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun tripleDESDecodeStr(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto("DESede/${mode}/${padding}", key, iv).decryptStr(data)
    }

    /**
     * 3DES解密，算法参数经过Base64加密
     *
     * @param data 加密的字符串
     * @param key Base64后的密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv Base64后的加盐
     * @return 解密后的字符串
     */
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).decryptStr(data)")
    )
    @JavascriptInterface
    fun tripleDESDecodeArgsBase64Str(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto(
            "DESede/${mode}/${padding}",
            Base64.decode(key, Base64.NO_WRAP),
            iv.encodeToByteArray()
        ).decryptStr(data)
    }


    /**
     * 3DES加密并转为Base64
     *
     * @param data 被加密的字符串
     * @param key 密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv 加盐
     * @return 加密后的Base64
     */
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data)")
    )
    @JavascriptInterface
    fun tripleDESEncodeBase64Str(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto("DESede/${mode}/${padding}", key, iv)
            .encryptBase64(data)
    }

    /**
     * 3DES加密并转为Base64，算法参数经过Base64加密
     *
     * @param data 被加密的字符串
     * @param key Base64后的密钥
     * @param mode 模式
     * @param padding 补码方式
     * @param iv Base64后的加盐
     * @return 加密后的Base64
     */
    @Deprecated(
        "过于繁琐弃用,但是web需要调用",
        ReplaceWith("createSymmetricCrypto(transformation, key, iv).encryptBase64(data)")
    )
    @JavascriptInterface
    fun tripleDESEncodeArgsBase64Str(
        data: String,
        key: String,
        mode: String,
        padding: String,
        iv: String
    ): String? {
        return createSymmetricCrypto(
            "DESede/${mode}/${padding}",
            Base64.decode(key, Base64.NO_WRAP),
            iv.encodeToByteArray()
        ).encryptBase64(data)
    }

//******************消息摘要/散列消息鉴别码************************//

    /**
     * 生成摘要，并转为16进制字符串
     *
     * @param data 被摘要数据
     * @param algorithm 签名算法
     * @return 16进制字符串
     */
    @JavascriptInterface
    fun digestHex(
        data: String,
        algorithm: String,
    ): String {
        return DigestUtil.digester(algorithm).digestHex(data)
    }

    /**
     * 生成摘要，并转为Base64字符串
     *
     * @param data 被摘要数据
     * @param algorithm 签名算法
     * @return Base64字符串
     */
    @JavascriptInterface
    fun digestBase64Str(
        data: String,
        algorithm: String,
    ): String {
        return Base64.encodeToString(DigestUtil.digester(algorithm).digest(data), Base64.NO_WRAP)
    }

    /**
     * 生成散列消息鉴别码，并转为16进制字符串
     *
     * @param data 被摘要数据
     * @param algorithm 签名算法
     * @param key 密钥
     * @return 16进制字符串
     */
    @Suppress("FunctionName")
    @JavascriptInterface
    fun HMacHex(
        data: String,
        algorithm: String,
        key: String
    ): String {
        return HMac(algorithm, key.toByteArray()).digestHex(data)
    }

    /**
     * 生成散列消息鉴别码，并转为Base64字符串
     *
     * @param data 被摘要数据
     * @param algorithm 签名算法
     * @param key 密钥
     * @return Base64字符串
     */
    @Suppress("FunctionName")
    @JavascriptInterface
    fun HMacBase64(
        data: String,
        algorithm: String,
        key: String
    ): String {
        return Base64.encodeToString(
            HMac(algorithm, key.toByteArray()).digest(data),
            Base64.NO_WRAP
        )
    }

//******************PBKDF2密钥派生************************//

    /**
     * PBKDF2 密钥派生，并转为16进制字符串
     *
     * 使用指定HMAC摘要算法对 password 与 salt 进行 PBKDF2 迭代派生。
     * 注意 password/salt 按 UTF-8 编码为字节，若为任意二进制(如 nonce‖counter)请改用
     * [pbkdf2HexByHex]，否则会被 UTF-8 编码破坏。
     * 底层基于 javax.crypto.Mac 原生实现，兼容全部 minSdk 版本，
     * 不依赖 API 26 才提供的 SecretKeyFactory "PBKDF2WithHmacSHA256"。
     *
     * @param password 口令(UTF-8)
     * @param salt 盐值(UTF-8)
     * @param algorithm HMAC算法, 如 HmacSHA256 / SHA-256
     * @param iterations 迭代次数, 需 >= 1
     * @param keyLength 派生密钥长度(字节), 需 >= 1
     * @return 16进制字符串
     */
    @JavascriptInterface
    fun pbkdf2Hex(
        password: String,
        salt: String,
        algorithm: String,
        iterations: Int,
        keyLength: Int
    ): String {
        return HexUtil.encodeHexStr(
            pbkdf2Derive(
                password.encodeToByteArray(), salt.encodeToByteArray(),
                algorithm, iterations, keyLength
            )
        )
    }

    /**
     * PBKDF2 密钥派生，并转为Base64字符串
     *
     * 注意 password/salt 按 UTF-8 编码为字节，若为任意二进制请改用 [pbkdf2Base64ByHex]。
     *
     * @param password 口令(UTF-8)
     * @param salt 盐值(UTF-8)
     * @param algorithm HMAC算法, 如 HmacSHA256 / SHA-256
     * @param iterations 迭代次数, 需 >= 1
     * @param keyLength 派生密钥长度(字节), 需 >= 1
     * @return Base64字符串
     */
    @JavascriptInterface
    fun pbkdf2Base64(
        password: String,
        salt: String,
        algorithm: String,
        iterations: Int,
        keyLength: Int
    ): String {
        return Base64.encodeToString(
            pbkdf2Derive(
                password.encodeToByteArray(), salt.encodeToByteArray(),
                algorithm, iterations, keyLength
            ),
            Base64.NO_WRAP
        )
    }

    /**
     * PBKDF2 密钥派生(二进制安全)，并转为16进制字符串
     *
     * passwordHex/saltHex 以16进制字符串传入，原生解码为字节后再派生，避免 UTF-8 编码破坏
     * 任意二进制口令(如 nonce(16字节)‖counter(4字节))。同时全程不依赖 java.lang.reflect，
     * 不受 RhinoClassShutter 屏蔽影响。
     *
     * @param passwordHex 口令(16进制字符串, 每字节2位)
     * @param saltHex 盐值(16进制字符串, 每字节2位)
     * @param algorithm HMAC算法, 如 HmacSHA256 / SHA-256
     * @param iterations 迭代次数, 需 >= 1
     * @param keyLength 派生密钥长度(字节), 需 >= 1
     * @return 16进制字符串
     */
    @JavascriptInterface
    fun pbkdf2HexByHex(
        passwordHex: String,
        saltHex: String,
        algorithm: String,
        iterations: Int,
        keyLength: Int
    ): String {
        return HexUtil.encodeHexStr(
            pbkdf2Derive(
                decodeHexBytes(passwordHex, "password"),
                decodeHexBytes(saltHex, "salt"),
                algorithm, iterations, keyLength
            )
        )
    }

    /**
     * PBKDF2 密钥派生(二进制安全)，并转为Base64字符串
     *
     * @param passwordHex 口令(16进制字符串, 每字节2位)
     * @param saltHex 盐值(16进制字符串, 每字节2位)
     * @param algorithm HMAC算法, 如 HmacSHA256 / SHA-256
     * @param iterations 迭代次数, 需 >= 1
     * @param keyLength 派生密钥长度(字节), 需 >= 1
     * @return Base64字符串
     */
    @JavascriptInterface
    fun pbkdf2Base64ByHex(
        passwordHex: String,
        saltHex: String,
        algorithm: String,
        iterations: Int,
        keyLength: Int
    ): String {
        return Base64.encodeToString(
            pbkdf2Derive(
                decodeHexBytes(passwordHex, "password"),
                decodeHexBytes(saltHex, "salt"),
                algorithm, iterations, keyLength
            ),
            Base64.NO_WRAP
        )
    }

    /**
     * PBKDF2 密钥派生核心实现(RFC 8018)
     *
     * DK = T1 || T2 || ... || Tl, 其中 Ti = F(P, S, c, i),
     * F(P, S, c, i) = U1 xor U2 xor ... xor Uc,
     * U1 = PRF(P, S || INT_32_BE(i)), Uj = PRF(P, U(j-1))。
     * 直接基于 javax.crypto.Mac 实现，避免记录口令、盐值等敏感信息。
     */
    private fun pbkdf2Derive(
        password: ByteArray,
        salt: ByteArray,
        algorithm: String,
        iterations: Int,
        keyLength: Int
    ): ByteArray {
        val hmacAlgorithm = normalizeHmacAlgorithm(algorithm)
        try {
            require(iterations >= 1) { "PBKDF2迭代次数必须 >= 1, 当前为 $iterations" }
            require(keyLength >= 1) { "PBKDF2派生密钥长度必须 >= 1, 当前为 $keyLength" }
            val mac = Mac.getInstance(hmacAlgorithm)
            mac.init(SecretKeySpec(password, hmacAlgorithm))
            val hLen = mac.macLength
            val blockCount = (keyLength + hLen - 1) / hLen
            val derivedKey = ByteArray(keyLength)
            val saltBlock = ByteArray(salt.size + 4)
            System.arraycopy(salt, 0, saltBlock, 0, salt.size)
            val u = ByteArray(hLen)
            val t = ByteArray(hLen)
            var offset = 0
            for (blockIndex in 1..blockCount) {
                // 大端序写入32位块序号 INT_32_BE(i)
                saltBlock[salt.size] = (blockIndex ushr 24).toByte()
                saltBlock[salt.size + 1] = (blockIndex ushr 16).toByte()
                saltBlock[salt.size + 2] = (blockIndex ushr 8).toByte()
                saltBlock[salt.size + 3] = blockIndex.toByte()
                // U1 = PRF(P, S || INT_32_BE(i))
                mac.update(saltBlock)
                mac.doFinal(u, 0)
                System.arraycopy(u, 0, t, 0, hLen)
                // Uj = PRF(P, U(j-1)); Ti = U1 xor U2 xor ... xor Uc
                for (round in 2..iterations) {
                    mac.update(u)
                    mac.doFinal(u, 0)
                    for (index in 0 until hLen) {
                        t[index] = (t[index].toInt() xor u[index].toInt()).toByte()
                    }
                }
                val copyLength = minOf(hLen, keyLength - offset)
                System.arraycopy(t, 0, derivedKey, offset, copyLength)
                offset += copyLength
            }
            AppLog.putDebug(
                "PBKDF2密钥派生完成 algorithm=$hmacAlgorithm iterations=$iterations keyLength=$keyLength"
            )
            return derivedKey
        } catch (e: Exception) {
            AppLog.put(
                "PBKDF2密钥派生失败 algorithm=$hmacAlgorithm iterations=$iterations keyLength=$keyLength",
                e
            )
            throw e
        }
    }

    /**
     * 解码16进制字符串为字节, 失败时记录错误日志(不记录密文本身)
     */
    private fun decodeHexBytes(hex: String, fieldName: String): ByteArray {
        return try {
            HexUtil.decodeHex(hex)
        } catch (e: Exception) {
            AppLog.put("PBKDF2 $fieldName 十六进制解码失败", e)
            throw e
        }
    }

    /**
     * 归一化 HMAC 算法名, 兼容 HmacSHA256 / SHA-256 / SHA256 等写法
     */
    private fun normalizeHmacAlgorithm(algorithm: String): String {
        val name = algorithm.trim()
        return if (name.startsWith("Hmac", ignoreCase = true)) {
            name
        } else {
            "Hmac" + name.replace("-", "").uppercase()
        }
    }

}