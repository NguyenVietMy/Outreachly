package com.pulse.pulse.activity.domain;

import java.util.List;

public interface GitHubRepositoryReader {

    List<GitHubRepository> findByUserIdOrderByPushedAtDesc(Long userId);
}
