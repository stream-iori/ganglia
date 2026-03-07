package work.ganglia.web.model;

/**
 * Generic request sent from client to server.
 */
public record ClientRequest(
    String action,     // 枚举: START, RESPOND_ASK, CANCEL
    String sessionId,
    Object payload     // 根据 action 不同反序列化为不同对象
) {
    public record StartPayload(String prompt) {}
    public record RespondAskPayload(String askId, String selectedOption) {}
}
