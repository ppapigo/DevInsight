import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Cell,
} from "recharts";
const colors = ["var(--chart-1)", "var(--chart-2)"];
export default function ActivityChart({ data }) {
  return (
    <ResponsiveContainer width="100%" height={220}>
      <BarChart data={data}>
        <XAxis
          dataKey="day"
          axisLine={false}
          tickLine={false}
          tick={{ fill: "var(--muted)", fontSize: 12 }}
        />
        <YAxis
          allowDecimals={false}
          axisLine={false}
          tickLine={false}
          tick={{ fill: "var(--muted)", fontSize: 12 }}
        />
        <Tooltip
          cursor={{ fill: "var(--accent-soft)" }}
          contentStyle={{
            background: "var(--surface)",
            border: "1px solid var(--border)",
            color: "var(--text)",
            borderRadius: 8,
          }}
          itemStyle={{ color: "var(--text)" }}
        />
        <Bar dataKey="count" name="표본 커밋" radius={[5, 5, 0, 0]}>
          {data.map((_, i) => (
            <Cell fill={colors[i % 2]} key={i} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
