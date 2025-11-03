
# MicVisualizerView

高性能麦克风音量可视化组件（Android Library）

<img width="750" height="625" alt="image" src="https://github.com/user-attachments/assets/023d7581-c570-4b0e-be00-fb8294c8c357" />

![Uploading c1f38a88e04f5c270706aa18654694bc_4.gif…]()

---

## 特性

- GPU 加速中心能量球 + 粒子拖尾 + 波纹动画  
- 支持动态 RMS 音量显示（平滑响应）  
- 呼吸效果（静音时缓慢呼吸闪烁）  
- 可通过 XML 自定义颜色、粒子数量、半径比例等属性  
- 支持 JitPack 一键集成  

---

## 安装（JitPack）

### 1️⃣ 在根 `build.gradle` 添加仓库

```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
````

### 2️⃣ 在 App Module `build.gradle` 添加依赖

```gradle
dependencies {
    implementation 'com.github.zhangyu789:MicVisualizerView:v1.0.0'
}
```

---

## XML 使用示例

```xml
<com.zy.view.MicVisualizerView
    android:id="@+id/micVisualizer"
    android:layout_width="match_parent"
    android:layout_height="150dp"
    app:coreColor="#FF4081"
    app:edgeColor="#448AFF"
    app:particleColor="#00FFFF"
    app:particleCount="20"
    app:maxRms="2500"
    app:baseRadiusRatio="0.18"/>
```

### 可配置属性

| 属性名               | 类型      | 默认值       | 说明             |
| ----------------- | ------- | --------- | -------------- |
| `coreColor`       | color   | `#448AFF` | 中心球体颜色         |
| `edgeColor`       | color   | `#448AFF` | 外层渐变颜色         |
| `particleColor`   | color   | `#448AFF` | 粒子颜色           |
| `particleCount`   | integer | `14`      | 粒子数量           |
| `maxRms`          | float   | `2000`    | 音量归一化最大值       |
| `baseRadiusRatio` | float   | `0.16`    | 中心球体基准半径占父布局比例 |

---

## 动态控制 RMS

```java
MicVisualizerView visualizer = findViewById(R.id.micVisualizer);

// 设置当前 RMS 音量
visualizer.setRmsLevel(500f);

// 可选：修改最大 RMS
visualizer.setMaxRms(2500f);
```

---

## 更新日志

* v1.0.0

  * 初始版本，支持 XML 属性和动态 RMS
  * GPU 优化粒子拖尾和中心球呼吸效果

---

## License

MIT License


