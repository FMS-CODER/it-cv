import axios from "@/axios";

// 查询 Markdown 问答文件分页列表
export function findMarkdownFilePageList(current, size, fileName, startDate, endDate) {
    return axios.post("/customer-service/md/list", {current, size, fileName, startDate, endDate})
}

// 上传 Markdown 问答文件
export function uploadMarkdownFile(form) {
    return axios.post("/customer-service/md/upload", form)
}

// 删除 Markdown 问答文件
export function deleteMarkdownFile(id) {
    return axios.post("/customer-service/md/delete", { id })
}

// 修改 Markdown 问答文件
export function updateMarkdownFile(record) {
    return axios.post("/customer-service/md/update", record)
}
