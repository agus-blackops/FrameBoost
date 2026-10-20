// Flat vertex-colored triangles (everything the game draws except text). Works in the built-in pipeline and in URP.
Shader "Trastazo/Solid"
{
    SubShader
    {
        Tags { "Queue" = "Transparent" "RenderType" = "Transparent" "IgnoreProjector" = "True" }
        Cull Off
        ZWrite Off
        ZTest Always
        Blend SrcAlpha OneMinusSrcAlpha
        Pass
        {
            CGPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            #include "UnityCG.cginc"

            struct appdata { float4 vertex : POSITION; fixed4 color : COLOR; };
            struct v2f { float4 pos : SV_POSITION; fixed4 color : COLOR; };

            v2f vert(appdata v)
            {
                v2f o;
                o.pos = UnityObjectToClipPos(v.vertex);
                o.color = v.color;
#ifndef UNITY_COLORSPACE_GAMMA
                o.color.rgb = GammaToLinearSpace(o.color.rgb);
#endif
                return o;
            }

            fixed4 frag(v2f i) : SV_Target { return i.color; }
            ENDCG
        }
    }
}
