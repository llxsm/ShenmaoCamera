package com.shenmao.camera.processing

/**
 * 摄影常用滤镜（20 种，与原桌面版 `build_filters()` 一一对应）。
 * 参数语义：temp 色温 / tint 色调 / hue 色相偏移 / sat 饱和度 / gamma 曲线 / contrast 对比度 / vignette 暗角。
 */
data class Filter(
    val name: String,
    val identity: Boolean = false,
    val temp: Double = 0.0,
    val tint: Double = 0.0,
    val hue: Double = 0.0,
    val sat: Double = 1.0,
    val gamma: Double = 1.0,
    val contrast: Double = 1.0,
    val bright: Double = 0.0,
    val vignette: Double = 0.0,
    val desc: String = "",
)

object Filters {
    val ALL: List<Filter> = listOf(
        Filter("原图", identity = true, desc = "不添加任何滤镜，展示摄像头原始画面。"),
        Filter("自然", temp = 0.0, tint = 0.0, hue = 0.0, sat = 1.05, gamma = 1.00, contrast = 1.02, vignette = 0.00,
            desc = "中性微调，真实自然，接近原片质感。"),
        Filter("鲜艳", temp = 5.0, tint = 0.0, hue = 0.0, sat = 1.35, gamma = 1.05, contrast = 1.10, vignette = 0.00,
            desc = "提升饱和度与对比度，色彩更鲜活明快。"),
        Filter("黑白", temp = 0.0, tint = 0.0, hue = 0.0, sat = 0.00, gamma = 1.05, contrast = 1.20, vignette = 0.15,
            desc = "经典黑白，去除色彩，强调明暗与质感。"),
        Filter("人像", temp = 15.0, tint = 8.0, hue = 0.0, sat = 1.10, gamma = 0.95, contrast = 0.95, vignette = 0.10,
            desc = "暖调柔肤，肤色红润通透，适合拍人。"),
        Filter("日系", temp = -10.0, tint = 0.0, hue = 0.0, sat = 0.85, gamma = 0.88, contrast = 0.88, vignette = 0.05,
            desc = "明亮低饱和，通透清新，文艺日系风。"),
        Filter("清新", temp = -15.0, tint = 0.0, hue = 0.0, sat = 1.05, gamma = 0.92, contrast = 0.95, vignette = 0.00,
            desc = "明亮冷调，清爽干净，适合风景与人像。"),
        Filter("胶片", temp = 10.0, tint = 0.0, hue = 0.0, sat = 0.95, gamma = 1.05, contrast = 1.10, vignette = 0.35,
            desc = "模拟胶片颗粒与暗角，复古耐看。"),
        Filter("复古", temp = 25.0, tint = -5.0, hue = 0.0, sat = 0.75, gamma = 0.90, contrast = 0.90, vignette = 0.40,
            desc = "暖黄褪色，怀旧复古质感。"),
        Filter("褪色", temp = 5.0, tint = 0.0, hue = 0.0, sat = 0.60, gamma = 0.85, contrast = 0.85, vignette = 0.45,
            desc = "低饱和低对比，柔和怀旧褪色感。"),
        Filter("暖阳", temp = 35.0, tint = -3.0, hue = 10.0, sat = 1.10, gamma = 0.95, contrast = 1.00, vignette = 0.10,
            desc = "温暖金黄，如夕阳下的柔和光线。"),
        Filter("冷调", temp = -30.0, tint = 0.0, hue = 0.0, sat = 1.00, gamma = 1.00, contrast = 1.00, vignette = 0.00,
            desc = "偏冷色调，干净清冷。"),
        Filter("蓝调", temp = -40.0, tint = -5.0, hue = 0.0, sat = 0.95, gamma = 1.00, contrast = 1.05, vignette = 0.20,
            desc = "蓝色氛围，静谧忧郁的电影感。"),
        Filter("青橙", temp = 15.0, tint = -10.0, hue = 0.0, sat = 1.15, gamma = 1.08, contrast = 1.15, vignette = 0.15,
            desc = "电影青橙色调，暗部偏青、亮部偏橙。"),
        Filter("黄昏", temp = 30.0, tint = 15.0, hue = 0.0, sat = 1.05, gamma = 0.95, contrast = 1.00, vignette = 0.25,
            desc = "橙紫暮色，浪漫黄昏氛围。"),
        Filter("高对比", temp = 0.0, tint = 0.0, hue = 0.0, sat = 1.15, gamma = 1.15, contrast = 1.35, vignette = 0.15,
            desc = "增强明暗对比，锐利立体有冲击力。"),
        Filter("柔和", temp = 5.0, tint = 0.0, hue = 0.0, sat = 0.90, gamma = 0.90, contrast = 0.90, vignette = 0.10,
            desc = "柔化高光阴影，温柔梦幻。"),
        Filter("梦幻", temp = 10.0, tint = 5.0, hue = 0.0, sat = 0.80, gamma = 0.85, contrast = 0.82, vignette = 0.30,
            desc = "低对比柔光，朦胧梦幻。"),
        Filter("银盐", temp = 0.0, tint = 0.0, hue = 0.0, sat = 0.00, gamma = 1.15, contrast = 1.40, vignette = 0.30,
            desc = "高反差黑白，颗粒感强，银盐胶片味。"),
        Filter("暗角", temp = 0.0, tint = 0.0, hue = 0.0, sat = 1.00, gamma = 1.00, contrast = 1.00, vignette = 0.60,
            desc = "四周压暗，突出中心主体。"),
    )
}
