package com.thepan.entity.vo.file;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaginationResultVO<T> {
	private Integer totalCount;
	private Integer pageSize;
	private Integer pageNo;
	private Integer pageTotal;
	private List<T> list;
}
