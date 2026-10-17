import Gio from 'gi://Gio';

for (const path of ARGV) {
    const [, bytes] = Gio.File.new_for_path(path).load_contents(null);
    Reflect.parse(new TextDecoder().decode(bytes), {source: path, target: 'module'});
    print(`Syntax OK: ${path}`);
}
