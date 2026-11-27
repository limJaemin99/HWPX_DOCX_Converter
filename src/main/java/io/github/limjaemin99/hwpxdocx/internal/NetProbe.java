package io.github.limjaemin99.hwpxdocx.internal;

// 네트워크 검사가 실패하는지 확인용. 합치지 않음
final class NetProbe {
    static Object probe() throws Exception {
        return new java.net.URL("http://127.0.0.1:9/").openConnection();
    }
}
