package com.example.core.camera

/**
 * Operating modes for the 3D kitchen camera navigation.
 */
enum class CameraMode(val title: String, val description: String) {
    ORBIT("مدار 3D", "تدوير حر حول مركز المطبخ مع تقريب وإزاحة"),
    FIRST_PERSON_WALK("وضع المشي", "تجول تفاعلي بالمنظور الشخصي بارتفاع 160 سم مع منع اختراق الجدران"),
    TOP_DOWN("مسقط علوي", "رؤية عمودية من الأعلى لدراسة المخطط وتوزيع المساحات")
}

/**
 * Camera preset views for instant cinematic tween transitions.
 */
enum class CameraNavigationPreset(val title: String, val iconName: String) {
    PERSPECTIVE("منظور عام", "perspective"),
    ISOMETRIC("أيزومتري 45°", "isometric"),
    TOP_VIEW("مسقط علوي", "top_view"),
    FRONT_ELEVATION("واجهة أمامية", "front"),
    SIDE_ELEVATION("واجهة جانبية", "side"),
    FIRST_PERSON("دخول المشي", "walkthrough"),
    FOCUS_SELECTION("تركيز على العنصر", "focus")
}
