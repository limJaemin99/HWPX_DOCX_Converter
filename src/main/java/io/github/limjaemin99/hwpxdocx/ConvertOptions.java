package io.github.limjaemin99.hwpxdocx;

/**
 * 변환 설정. 만든 뒤에는 바뀌지 않으므로 여러 변환에서 같이 써도 된다.
 *
 * <pre>{@code
 * ConvertOptions opt = ConvertOptions.builder().failOnUnsupported(true).build();
 * }</pre>
 */
public final class ConvertOptions {

    private static final ConvertOptions DEFAULTS = builder().build();

    private final boolean failOnUnsupported;

    private ConvertOptions(Builder b) {
        this.failOnUnsupported = b.failOnUnsupported;
    }

    /** 기본 설정. */
    public static ConvertOptions defaults() {
        return DEFAULTS;
    }

    /** 설정을 만드는 빌더. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 옮길 수 없는 요소를 만나면 변환을 멈출지.
     * 기본값 {@code false}: 건너뛰고 {@link ConvertResult#getSkipped()}에 남긴다.
     */
    public boolean isFailOnUnsupported() {
        return failOnUnsupported;
    }

    @Override
    public String toString() {
        return "ConvertOptions{failOnUnsupported=" + failOnUnsupported + "}";
    }

    /** {@link ConvertOptions} 빌더. */
    public static final class Builder {

        private boolean failOnUnsupported;

        private Builder() {
        }

        /** {@link ConvertOptions#isFailOnUnsupported()} 참고. */
        public Builder failOnUnsupported(boolean value) {
            this.failOnUnsupported = value;
            return this;
        }

        /** 설정을 만든다. */
        public ConvertOptions build() {
            return new ConvertOptions(this);
        }
    }
}
