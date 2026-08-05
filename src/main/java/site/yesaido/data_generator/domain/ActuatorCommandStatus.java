package site.yesaido.data_generator.domain;

public enum ActuatorCommandStatus {
    APPLIED, // 명령을 정상 적용했거나 동일 commandId의 기존 결과를 재반환
    REJECTED_EXPIRED, // 현재 시간이 expiresAt을 지남
    REJECTED_STALE, // 같은 액추에이터에 더 최신 명령이 이미 적용됨
    REJECTED_CONFLICT // commandId 재사용 충돌 또는 반대 방향 액추에이터 ON 충돌
}
