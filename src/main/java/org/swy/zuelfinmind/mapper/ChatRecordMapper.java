package org.swy.zuelfinmind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.swy.zuelfinmind.entity.ChatRecord;

@Mapper // 告诉Spring：这也是个要管理的组件，启动时扫描我
public interface ChatRecordMapper extends BaseMapper<ChatRecord> {
    // 里面什么都不用写！！！
    // BaseMapper已经帮忙写好了：insert，selectById，update，delete...
}
