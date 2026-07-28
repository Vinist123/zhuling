package com.vinist.domain.agent.service;

import com.vinist.domain.agent.model.ChatTarget;
import com.vinist.domain.agent.model.ChatTargetType;

import java.util.List;

public interface IChatTargetService {

    List<ChatTarget> listTargets();

    ChatTarget getRequired(ChatTargetType type, String id);

}
