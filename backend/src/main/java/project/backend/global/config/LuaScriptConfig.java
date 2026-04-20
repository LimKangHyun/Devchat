package project.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

@Configuration
public class LuaScriptConfig {

    @Bean
    public DefaultRedisScript<Long> genMessageSeqScript() {
        return loadScript("scripts/gen_message_seq.lua", Long.class);
    }

    @Bean
    public DefaultRedisScript<Long> recoverAndIncrScript() {
        return loadScript("scripts/recover_and_incr.lua", Long.class);
    }

    @Bean
    public DefaultRedisScript<List> getAndClearUpdatedRoomsScript() {
        return loadScript("scripts/get_and_clear_updated_rooms.lua", List.class);
    }

    @Bean
    public DefaultRedisScript<Long> setSequenceScript() {
        return loadScript("scripts/set_sequence.lua", Long.class);
    }

    @Bean
    public DefaultRedisScript<Long> tokenBucketScript() {
        return loadScript("scripts/token_bucket.lua", Long.class);
    }

    private <T> DefaultRedisScript<T> loadScript(String path, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(resultType);
        return script;
    }
}