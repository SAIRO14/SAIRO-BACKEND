package com.sairo.sairo_backend.common;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@link DeviceId}가 붙은 파라미터에 {@code X-Device-Id} 헤더 값을 넣는다.
 *
 * <p>헤더 값은 절대 로그에 남기지 않는다. 오류를 던질 때도 값을 메시지에 넣지 않는다.
 * (docs/api-contract.md §4, §7)
 *
 * <p>UUID v4만 받는다. {@code UUID.fromString}은 {@code "1-1-1-1-1"} 같은 축약형도
 * 통과시키고 버전도 보지 않으므로 쓰지 않고 정규식으로 판정한다.
 */
@Component
public class DeviceIdArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_NAME = "X-Device-Id";

    /** 8-4-4-4-12 정규형 UUID 중 버전 4, IETF variant(8·9·a·b)만 통과한다. */
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(DeviceId.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        if (type != String.class && type != Optional.class) {
            throw new IllegalStateException(
                    "@DeviceId는 String 또는 Optional<String>에만 붙일 수 있다: " + parameter.getExecutable());
        }
        return true;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        boolean optional = parameter.getParameterType() == Optional.class;
        String raw = webRequest.getHeader(HEADER_NAME);

        // 값이 빈 헤더는 없는 것과 같게 다룬다. 클라이언트가 초기화 전 빈 문자열을 붙여 보내는 경우가 있다.
        if (raw == null || raw.isBlank()) {
            if (optional) {
                return Optional.empty();
            }
            throw new BusinessException(ErrorCode.DEVICE_ID_REQUIRED);
        }

        String value = raw.trim();
        // 선택 파라미터라도 형식이 틀리면 실패시킨다. 조용히 무시하면 클라이언트가
        // 저장 여부가 늘 false인 이유를 알 수 없다.
        if (!UUID_V4.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.DEVICE_ID_INVALID);
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        return optional ? Optional.of(normalized) : normalized;
    }
}
