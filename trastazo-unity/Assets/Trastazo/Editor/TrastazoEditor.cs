using System.IO;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace TrastazoUnityEditor
{
    /** Project setup and the Trastazo menu: create the scene, build for Android / Windows / Linux / macOS. */
    [InitializeOnLoad]
    public static class TrastazoEditor
    {
        private const string ScenePath = "Assets/Scenes/Trastazo.unity";
        private const string SetupKey = "Trastazo.ProjectSetupDone";

        static TrastazoEditor()
        {
            // once per machine: sensible player settings for this game
            if (EditorPrefs.GetBool(SetupKey + Application.dataPath, false)) return;
            EditorApplication.delayCall += () =>
            {
                ConfigurePlayerSettings();
                EditorPrefs.SetBool(SetupKey + Application.dataPath, true);
            };
        }

        [MenuItem("Trastazo/Configurar proyecto")]
        public static void ConfigurePlayerSettings()
        {
            PlayerSettings.productName = "Trastazo";
            PlayerSettings.companyName = "FrameBoost";
            PlayerSettings.colorSpace = ColorSpace.Gamma;          // the game's colors are plain sRGB values
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.AutoRotation;
            PlayerSettings.allowedAutorotateToPortrait = false;
            PlayerSettings.allowedAutorotateToPortraitUpsideDown = false;
            PlayerSettings.allowedAutorotateToLandscapeLeft = true;
            PlayerSettings.allowedAutorotateToLandscapeRight = true;
            PlayerSettings.runInBackground = false;
            PlayerSettings.SetScriptingBackend(BuildTargetGroup.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARMv7 | AndroidArchitecture.ARM64;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel23;
#if UNITY_2021_2_OR_NEWER
            PlayerSettings.SetApplicationIdentifier(UnityEditor.Build.NamedBuildTarget.Android, "com.frameboost.trastazo");
#else
            PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, "com.frameboost.trastazo");
#endif
            Debug.Log("Trastazo: ajustes del proyecto configurados.");
        }

        [MenuItem("Trastazo/Crear escena")]
        public static void CreateScene()
        {
            Directory.CreateDirectory("Assets/Scenes");
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            var go = new GameObject("Trastazo");
            go.AddComponent<TrastazoApp>();
            EditorSceneManager.SaveScene(scene, ScenePath);
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
            Debug.Log("Trastazo: escena creada en " + ScenePath);
        }

        private static string[] Scenes()
        {
            if (!File.Exists(ScenePath)) CreateScene();
            return new[] { ScenePath };
        }

        private static void Build(BuildTarget target, string path)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            var options = new BuildPlayerOptions { scenes = Scenes(), locationPathName = path, target = target, options = BuildOptions.None };
            var report = BuildPipeline.BuildPlayer(options);
            Debug.Log("Trastazo: compilación " + target + " -> " + report.summary.result + " (" + path + ")");
            if (Application.isBatchMode && report.summary.result != UnityEditor.Build.Reporting.BuildResult.Succeeded) EditorApplication.Exit(1);
        }

        [MenuItem("Trastazo/Compilar/Android (APK)")]
        public static void BuildAndroid() { EditorUserBuildSettings.buildAppBundle = false; Build(BuildTarget.Android, "Builds/Android/Trastazo.apk"); }

        [MenuItem("Trastazo/Compilar/Windows (64 bits)")]
        public static void BuildWindows() { Build(BuildTarget.StandaloneWindows64, "Builds/Windows/Trastazo.exe"); }

        [MenuItem("Trastazo/Compilar/Linux (64 bits)")]
        public static void BuildLinux() { Build(BuildTarget.StandaloneLinux64, "Builds/Linux/Trastazo.x86_64"); }

        [MenuItem("Trastazo/Compilar/macOS")]
        public static void BuildMac() { Build(BuildTarget.StandaloneOSX, "Builds/macOS/Trastazo.app"); }
    }
}
