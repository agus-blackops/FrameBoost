using Godot;

namespace TrastazoGodot
{
    /** The workshop's mod editor: a text box over the game, like the Android dialog and the PC window. */
    public partial class ModEditor : CanvasLayer
    {
        private Trastazo.Game game;
        private Trastazo.Mod existing;
        private PanelContainer panel;
        private Label title, error;
        private TextEdit edit;
        private Button delete;
        private ColorRect shade;

        public bool IsOpen { get { return Visible; } }

        public override void _Ready()
        {
            Layer = 50;
            Visible = false;
            shade = new ColorRect();
            shade.Color = new Color(0f, 0f, 0f, 0.6f);
            shade.SetAnchorsPreset(Control.LayoutPreset.FullRect);
            AddChild(shade);

            CenterContainer center = new CenterContainer();
            center.SetAnchorsPreset(Control.LayoutPreset.FullRect);
            AddChild(center);
            panel = new PanelContainer();
            panel.CustomMinimumSize = new Vector2(640, 440);
            center.AddChild(panel);
            MarginContainer margin = new MarginContainer();
            margin.AddThemeConstantOverride("margin_left", 12);
            margin.AddThemeConstantOverride("margin_right", 12);
            margin.AddThemeConstantOverride("margin_top", 10);
            margin.AddThemeConstantOverride("margin_bottom", 10);
            panel.AddChild(margin);
            VBoxContainer box = new VBoxContainer();
            margin.AddChild(box);

            title = new Label();
            title.Text = "Crear mod";
            box.AddChild(title);
            edit = new TextEdit();
            edit.CustomMinimumSize = new Vector2(0, 170);
            edit.SizeFlagsVertical = Control.SizeFlags.ExpandFill;
            box.AddChild(edit);
            error = new Label();
            error.Modulate = new Color(1f, 0.55f, 0.5f);
            box.AddChild(error);
            ScrollContainer help = new ScrollContainer();
            help.CustomMinimumSize = new Vector2(0, 110);
            Label helpText = new Label();
            helpText.Text = Trastazo.Mod.HELP;
            helpText.AutowrapMode = TextServer.AutowrapMode.WordSmart;
            helpText.CustomMinimumSize = new Vector2(590, 0);
            help.AddChild(helpText);
            box.AddChild(help);
            HBoxContainer row = new HBoxContainer();
            box.AddChild(row);
            Button save = new Button(); save.Text = "Guardar"; save.SizeFlagsHorizontal = Control.SizeFlags.ExpandFill; save.Pressed += OnSave; row.AddChild(save);
            Button cancel = new Button(); cancel.Text = "Cancelar"; cancel.SizeFlagsHorizontal = Control.SizeFlags.ExpandFill; cancel.Pressed += OnCancel; row.AddChild(cancel);
            delete = new Button(); delete.Text = "Borrar"; delete.SizeFlagsHorizontal = Control.SizeFlags.ExpandFill; delete.Pressed += OnDelete; row.AddChild(delete);
        }

        public void Open(Trastazo.Game g, Trastazo.Mod mod)
        {
            game = g;
            existing = mod;
            title.Text = mod == null ? "Crear mod" : "Editar mod";
            edit.Text = mod != null ? mod.source : "name Mi Mod\ndesc Lo que hace\ngravity 0.5\nonce spawn human 3\nevery 3 spawn crate\n";
            error.Text = "";
            delete.Visible = mod != null;
            Visible = true;
        }

        private void OnSave()
        {
            string err = game.saveMod(existing, edit.Text);
            if (err != null) error.Text = err; else Visible = false;
        }

        private void OnCancel() { Visible = false; }

        private void OnDelete() { game.deleteMod(existing); Visible = false; }
    }
}
