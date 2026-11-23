package io.github.psung616.tossmcp.secret;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Windows 자격 증명 관리자(일반 자격 증명). advapi32 의 CredWriteW / CredReadW / CredDeleteW 를 JNA 로 직접 호출한다.
 * 저장된 값은 "제어판 > 자격 증명 관리자 > Windows 자격 증명" 에서 확인·삭제할 수 있다.
 */
final class WindowsCredentialManager implements OsKeyring {

    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    private static final int ERROR_NOT_FOUND = 1168;

    private final String target;

    WindowsCredentialManager(String target) {
        this.target = target;
    }

    @Override
    public Optional<Credentials> read() {
        PointerByReference ref = new PointerByReference();
        if (!Advapi32.INSTANCE.CredReadW(new WString(target), CRED_TYPE_GENERIC, 0, ref)) {
            int error = Native.getLastError();
            if (error == ERROR_NOT_FOUND) {
                return Optional.empty();
            }
            throw new IllegalStateException("자격 증명 관리자에서 읽지 못했습니다. (Windows 오류 " + error + ")");
        }
        Pointer p = ref.getValue();
        try {
            CREDENTIAL cred = new CREDENTIAL(p);
            byte[] blob = cred.CredentialBlob.getByteArray(0, cred.CredentialBlobSize);
            String secret = new String(blob, StandardCharsets.UTF_8);
            Arrays.fill(blob, (byte) 0);
            String clientId = cred.UserName == null ? null : cred.UserName.toString();
            if (clientId == null || clientId.isBlank() || secret.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new Credentials(clientId, secret));
        } finally {
            Advapi32.INSTANCE.CredFree(p);
        }
    }

    @Override
    public void write(String clientId, char[] clientSecret) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(clientSecret));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        Arrays.fill(encoded.array(), (byte) 0);

        Memory blob = new Memory(Math.max(bytes.length, 1));
        try {
            blob.write(0, bytes, 0, bytes.length);
            CREDENTIAL cred = new CREDENTIAL();
            cred.Type = CRED_TYPE_GENERIC;
            cred.TargetName = new WString(target);
            cred.Comment = new WString("tossinvest-mcp: 토스증권 Open API 키");
            cred.CredentialBlobSize = bytes.length;
            cred.CredentialBlob = blob;
            cred.Persist = CRED_PERSIST_LOCAL_MACHINE;
            cred.UserName = new WString(clientId);
            if (!Advapi32.INSTANCE.CredWriteW(cred, 0)) {
                throw new IllegalStateException("자격 증명 관리자에 저장하지 못했습니다. (Windows 오류 " + Native.getLastError() + ")");
            }
        } finally {
            Arrays.fill(bytes, (byte) 0);
            blob.clear();
            blob.close();
        }
    }

    @Override
    public void delete() {
        if (!Advapi32.INSTANCE.CredDeleteW(new WString(target), CRED_TYPE_GENERIC, 0)) {
            int error = Native.getLastError();
            if (error != ERROR_NOT_FOUND) {
                throw new IllegalStateException("자격 증명 관리자에서 삭제하지 못했습니다. (Windows 오류 " + error + ")");
            }
        }
    }

    /** advapi32.dll 바인딩 (필요한 함수만). */
    interface Advapi32 extends StdCallLibrary {
        Advapi32 INSTANCE = Native.load("Advapi32", Advapi32.class);

        boolean CredWriteW(CREDENTIAL credential, int flags);

        boolean CredReadW(WString targetName, int type, int flags, PointerByReference credential);

        boolean CredDeleteW(WString targetName, int type, int flags);

        void CredFree(Pointer buffer);
    }

    /** CREDENTIALW 구조체. LastWritten(FILETIME)은 DWORD 두 개로 표현한다. */
    @Structure.FieldOrder({
        "Flags",
        "Type",
        "TargetName",
        "Comment",
        "LastWrittenLow",
        "LastWrittenHigh",
        "CredentialBlobSize",
        "CredentialBlob",
        "Persist",
        "AttributeCount",
        "Attributes",
        "TargetAlias",
        "UserName"
    })
    public static class CREDENTIAL extends Structure {
        public int Flags;
        public int Type;
        public WString TargetName;
        public WString Comment;
        public int LastWrittenLow;
        public int LastWrittenHigh;
        public int CredentialBlobSize;
        public Pointer CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public Pointer Attributes;
        public WString TargetAlias;
        public WString UserName;

        public CREDENTIAL() {}

        CREDENTIAL(Pointer p) {
            super(p);
            read();
        }
    }
}
