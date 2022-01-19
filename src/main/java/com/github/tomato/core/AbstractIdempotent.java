package com.github.tomato.core;

import com.github.tomato.constant.TomatoConstant;
import com.github.tomato.util.Md5Tools;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * https://tomato.springlearn.cn/
 *
 * @author liuxin
 * 2019-12-29 22:37
 */
@Slf4j
public abstract class AbstractIdempotent implements Idempotent {

    private String prefix = "TMT_";

    /**
     * 滑动窗口
     *
     * @param uniqueCode  唯一键
     * @param millisecond 毫秒
     * @return boolean
     */
    @Override
    public boolean idempotent(String uniqueCode, String value, Long millisecond) {
        String uniqueToken = isolationAlgorithmToken(uniqueCode);
        log.debug("Idempotent: key[" + uniqueToken + "],expire:[" + millisecond + "ms]");
        boolean idempotent = doAddIdempotent(uniqueToken, value, millisecond);
        if (!idempotent) {
            //重复请求后,都会增加占用时间,以此来保证滑动窗口。只有当安全时间内没有请求,才会释放防重空间
            //图例说明
            //https://ss.csdn.net/p?https://upload-images.jianshu.io/upload_images/4279695-72b43f588ab828aa
            expire(uniqueToken, millisecond);
        }
        return idempotent;
    }

    @Override
    public String getIdempotent(String uniqueCode) {
        String uniqueToken = isolationAlgorithmToken(uniqueCode);
        return doGetIdempotent(uniqueToken);
    }

    /**
     * 删除幂等键
     *
     * @param uniqueCode 唯一码
     * @return boolean
     */
    @Override
    public boolean delIdempotent(String uniqueCode) {
        String uniqueToken = isolationAlgorithmToken(uniqueCode);
        log.debug("Del Idempotent: key[" + uniqueToken + "]");
        return delKey(uniqueToken);
    }

    /**
     * 滑动窗口
     *
     * @param uniqueCode        唯一键
     * @param millisecond       毫秒
     * @param exceptionSupplier 指定要抛的异常
     */
    @Override
    public <E extends Throwable> void idempotent(String uniqueCode, String value, Long millisecond, Supplier<? extends E> exceptionSupplier) throws E {
        if (!idempotent(uniqueCode, value, millisecond)) {
            throw exceptionSupplier.get();
        }
    }


    /**
     * 固定窗口
     *
     * @param uniqueCode  唯一键
     * @param millisecond 毫秒
     * @return boolean
     */
    @Override
    public boolean fixedIdempotent(String uniqueCode, String value, Long millisecond) {
        String uniqueToken = isolationAlgorithmToken(uniqueCode);
        log.debug("Idempotent: key[" + uniqueToken + "],expire:[" + millisecond + "ms]");
        return doAddIdempotent(uniqueToken, value, millisecond);
    }

    /**
     * 固定窗口
     *
     * @param uniqueCode        唯一键
     * @param millisecond       毫秒
     * @param exceptionSupplier 指定要抛的异常
     */
    @Override
    public <E extends Throwable> void fixedIdempotent(String uniqueCode, String value, Long millisecond, Supplier<? extends E> exceptionSupplier) throws E {
        if (!fixedIdempotent(uniqueCode, value, millisecond)) {
            throw exceptionSupplier.get();
        }
    }


    /**
     * 通过对唯一键进行处理,保证与业务数据进行隔离
     *
     * @param uniqueCode 明文唯一键
     * @return String
     */
    private String isolationAlgorithmToken(String uniqueCode) {
        return prefix + Md5Tools.md5(uniqueCode.trim());
    }

    /**
     * 允许子类重写改方法
     *
     * @param prefix 前缀
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 幂等处理
     *
     * @param uniqueToken 加密后的唯一键
     * @param millisecond 毫秒
     * @return boolean
     */
    protected abstract boolean doAddIdempotent(String uniqueToken, String value, Long millisecond);

    protected abstract String doGetIdempotent(String uniqueToken);

    /**
     * 过期key
     * 对指定的key进行时间限制
     *
     * @param uniqueToken 加密后的唯一键
     * @param millisecond 毫秒
     */
    public abstract void expire(String uniqueToken, Long millisecond);


    /**
     * 删除key
     *
     * @param uniqueToken 加密后的唯一键
     */
    public abstract boolean delKey(String uniqueToken);
}
