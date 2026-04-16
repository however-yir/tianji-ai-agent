package com.tianji.aigc.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.aigc.demo.DevDemoSessionStore;
import com.tianji.aigc.entity.ChatSession;
import com.tianji.aigc.mapper.ChatSessionMapper;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatSessionVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.aigc.vo.SessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Primary
@Profile("dev-demo")
@RequiredArgsConstructor
public class DevDemoChatSessionService extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final DevDemoSessionStore devDemoSessionStore;

    @Override
    public SessionVO createSession(Integer num) {
        return devDemoSessionStore.createSession(num);
    }

    @Override
    public List<SessionVO.Example> hotExamples(Integer num) {
        return devDemoSessionStore.hotExamples(num);
    }

    @Override
    public List<MessageVO> queryBySessionId(String sessionId) {
        return devDemoSessionStore.queryBySessionId(sessionId);
    }

    @Override
    public void update(String sessionId, String title, Long userId) {
        devDemoSessionStore.update(sessionId, title);
    }

    @Override
    public Map<String, List<ChatSessionVO>> queryHistorySession() {
        return devDemoSessionStore.queryHistorySession();
    }
}
