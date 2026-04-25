type IconProps = { className?: string };

export function SlackIcon({ className }: IconProps) {
  return <img src="/Slack_icon_2019.svg" alt="Slack" className={className} />;
}

export function LinearIcon({ className }: IconProps) {
  return <img src="/linear.svg" alt="Linear" className={className} />;
}

export function ObsidianIcon({ className }: IconProps) {
  return <img src="/obsidian-icon.svg" alt="Obsidian" className={className} />;
}

export function GitHubIcon({ className }: IconProps) {
  return <img src="/github-svgrepo-com.svg" alt="GitHub" className={className} />;
}
