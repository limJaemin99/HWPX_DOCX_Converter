package io.github.limjaemin99.hwpxdocx;

import java.io.IOException;

/**
 * 입력 문서를 읽을 수 없거나 변환할 수 없을 때 던진다.
 * {@link IOException}을 상속하므로 스트림 오류와 함께 잡아도 된다.
 */
public class ConvertException extends IOException {

    private static final long serialVersionUID = 1L;

    /** @param message 오류 내용 */
    public ConvertException(String message) {
        super(message);
    }

    /**
     * @param message 오류 내용
     * @param cause   원인
     */
    public ConvertException(String message, Throwable cause) {
        super(message, cause);
    }
}
